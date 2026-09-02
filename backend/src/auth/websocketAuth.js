import { z } from "zod";

const WsMessageSchema = z.discriminatedUnion("type", [
  z.object({ type: z.literal("command"), command: z.string().min(1).max(2000) }),
  z.object({ type: z.literal("ping") })
]);

export class WebSocketAuth {
  constructor(tokenService) {
    this.tokenService = tokenService;
    this.connections = new Map();
  }

  async handleConnection(ws, req) {
    const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
    const token = url.searchParams.get("token");
    const deviceId = url.searchParams.get("device");

    if (!token || !deviceId) {
      ws.close(4001, "Missing token or device ID");
      return;
    }

    const isValid = await this.tokenService.validateToken(deviceId, token);
    if (!isValid) {
      ws.close(4001, "Invalid token");
      return;
    }

    this.connections.set(ws, { deviceId, lastActivity: Date.now() });
    console.log(`WebSocket connected: ${deviceId}`);

    ws.on("message", async (raw) => {
      const connection = this.connections.get(ws);
      if (connection) {
        connection.lastActivity = Date.now();
      }

      try {
        const rawJson = JSON.parse(raw.toString());
        const parsed = WsMessageSchema.safeParse(rawJson);
        if (!parsed.success) {
          ws.send(JSON.stringify({ type: "error", message: "Invalid message format" }));
          return;
        }

        const msg = parsed.data;
        if (msg.type === "command") {
          ws.send(JSON.stringify({ type: "received", command: msg.command }));
        } else if (msg.type === "ping") {
          ws.send(JSON.stringify({ type: "pong", timestamp: Date.now() }));
        }
      } catch (err) {
        console.error("WS message error:", err.message);
        ws.send(JSON.stringify({ type: "error", message: err.message }));
      }
    });

    ws.on("close", () => {
      this.connections.delete(ws);
      console.log(`WebSocket disconnected: ${deviceId}`);
    });

    ws.send(JSON.stringify({
      type: "connected",
      deviceId,
      message: "JARVIS backend connected"
    }));
  }

  cleanupStaleConnections(maxIdleMs = 300000) {
    const now = Date.now();
    for (const [ws, connection] of this.connections.entries()) {
      if (now - connection.lastActivity > maxIdleMs) {
        ws.close(1000, "Idle timeout");
        this.connections.delete(ws);
      }
    }
  }
}