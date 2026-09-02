import express from "express";
import { z } from "zod";

const registerSchema = z.object({
  device_id: z.string().min(1),
  device_name: z.string().optional(),
  device_model: z.string().optional(),
  os_version: z.string().optional(),
  enrollment_secret: z.string().optional()
});

const refreshSchema = z.object({
  refresh_token: z.string().min(1)
});

export function createAuthRoutes(tokenService, enrollmentService, sessionService) {
  const router = express.Router();

  router.post("/enroll", async (req, res) => {
    try {
      const { device_id, device_name, device_model, os_version } = req.body;
      if (!device_id) {
        return res.status(400).json({ error: "device_id is required" });
      }
      if (!enrollmentService) {
        return res.status(503).json({ error: "Enrollment service not available (no Supabase)" });
      }

      const result = await enrollmentService.enrollDevice(
        device_id,
        device_name || "unknown",
        device_model || "unknown",
        os_version || "unknown"
      );

      if (!result.success) {
        return res.status(409).json({ error: result.error });
      }

      res.json({
        success: true,
        device_id,
        enrollment_secret: result.enrollmentSecret,
        message: "Enter this pairing code in the JARVIS app Settings > Pair Device"
      });
    } catch (error) {
      console.error("Enrollment error:", error.message);
      res.status(500).json({ error: "Enrollment failed" });
    }
  });

  router.post("/token", async (req, res) => {
    try {
      const validation = registerSchema.safeParse(req.body);
      if (!validation.success) {
        return res.status(400).json({ error: "Invalid request", details: validation.error.issues });
      }

      const { device_id, device_name, device_model, os_version, enrollment_secret } = validation.data;

      if (enrollmentService) {
        const existingDevice = await enrollmentService.getDevice(device_id);

        if (!existingDevice) {
          if (!enrollment_secret) {
            return res.status(403).json({
              error: "Device not enrolled",
              message: "Provide enrollment_secret to complete device enrollment"
            });
          }

          const verified = await enrollmentService.verifyEnrollment(device_id, enrollment_secret);
          if (!verified) {
            return res.status(403).json({ error: "Invalid enrollment secret" });
          }
        }
      }

      const result = await tokenService.createSession(device_id, device_name, device_model, os_version);
      res.json(result);
    } catch (error) {
      console.error("Registration error:", error.message);
      res.status(500).json({ error: "Registration failed" });
    }
  });

  router.post("/refresh", async (req, res) => {
    try {
      const validation = refreshSchema.safeParse(req.body);
      if (!validation.success) {
        return res.status(400).json({ error: "Invalid request", details: validation.error.issues });
      }

      const { refresh_token } = validation.data;
      const result = await tokenService.refreshTokens(refresh_token);
      if (!result) {
        return res.status(401).json({ error: "Invalid or expired refresh token" });
      }
      res.json(result);
    } catch (error) {
      console.error("Refresh error:", error.message);
      res.status(500).json({ error: "Refresh failed" });
    }
  });

  router.post("/revoke", async (req, res) => {
    try {
      const authHeader = req.headers.authorization;
      const deviceId = req.headers["x-device-id"];

      if (!authHeader || !authHeader.startsWith("Bearer ") || !deviceId) {
        return res.status(401).json({ error: "Authentication required" });
      }

      const token = authHeader.slice(7);
      const isValid = await tokenService.validateToken(deviceId, token);
      if (!isValid) {
        return res.status(401).json({ error: "Invalid token" });
      }

      if (sessionService) {
        await sessionService.revokeAllSessions(deviceId);
      }
      res.json({ success: true, message: "All sessions revoked" });
    } catch (error) {
      console.error("Revoke error:", error.message);
      res.status(500).json({ error: "Revoke failed" });
    }
  });

  return router;
}
