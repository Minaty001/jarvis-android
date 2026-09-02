import express from 'express';
import cors from 'cors';
import { WebSocketServer } from 'ws';
import { createServer } from 'http';
import { z } from 'zod';
import { CONFIG } from './config.js';
import { LLMOrchestrator } from './core/llmOrchestrator.js';
import { SessionManager } from './core/sessionManager.js';
import { CommandRouter } from './core/commandRouter.js';
import { MemoryManager } from './core/memoryManager.js';
import { DeviceSessionManager } from './core/deviceSessionManager.js';
import { healthRoutes } from './routes/health.js';
import { commandRoutes } from './routes/command.js';
import { memoryRoutes } from './routes/memory.js';
import { skillRoutes } from './routes/skill.js';
import { sanitizeMiddleware } from './middleware/sanitize.js';

const llm = new LLMOrchestrator();
const sessionManager = new SessionManager();
const memoryManager = new MemoryManager();
const deviceSessions = new DeviceSessionManager();
const commandRouter = new CommandRouter(llm, memoryManager);

const app = express();

const allowedOrigins = (CONFIG.allowedOrigins || '').split(',').filter(Boolean);
app.use(cors({
  origin: allowedOrigins.length > 0 ? allowedOrigins : '*',
  credentials: true,
}));
app.use(express.json({ limit: '1mb' }));
app.use(sanitizeMiddleware);
app.set('sessionManager', sessionManager);
app.set('deviceSessions', deviceSessions);

const requestCounts = new Map();
const RATE_LIMIT_WINDOW = 60000;
const RATE_LIMIT_MAX = 30;

app.use((req, res, next) => {
  const ip = req.ip || req.connection.remoteAddress || 'unknown';
  const now = Date.now();
  const record = requestCounts.get(ip) || { count: 0, resetAt: now + RATE_LIMIT_WINDOW };

  if (now > record.resetAt) {
    record.count = 0;
    record.resetAt = now + RATE_LIMIT_WINDOW;
  }

  record.count++;
  requestCounts.set(ip, record);

  if (record.count > RATE_LIMIT_MAX) {
    return res.status(429).json({ status: 'error', message: 'Rate limit exceeded.' });
  }

  res.setHeader('X-RateLimit-Remaining', Math.max(0, RATE_LIMIT_MAX - record.count));
  next();
});

app.use((req, res, next) => {
  const start = Date.now();
  res.on('finish', () => {
    const duration = Date.now() - start;
    if (duration > 5000) {
      console.warn(`Slow request: ${req.method} ${req.path} took ${duration}ms`);
    }
  });
  next();
});

async function authMiddleware(req, res, next) {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ status: 'error', message: 'Missing Authorization header' });
  }

  const token = authHeader.slice(7);
  const deviceId = req.headers['x-device-id'];

  if (!deviceId) {
    return res.status(401).json({ status: 'error', message: 'Missing X-Device-ID header' });
  }

  const valid = await deviceSessions.validateToken(deviceId, token);
  if (!valid) {
    return res.status(401).json({ status: 'error', message: 'Invalid or expired token' });
  }

  req.authenticatedDeviceId = deviceId;
  next();
}

app.post('/api/v1/auth/token', async (req, res) => {
  try {
    const { device_id, device_name, device_model, os_version } = req.body;
    if (!device_id) {
      return res.status(400).json({ error: 'device_id is required' });
    }
    const tokens = await deviceSessions.registerDevice({
      deviceId: device_id,
      deviceName: device_name,
      deviceModel: device_model,
      osVersion: os_version,
    });
    res.json(tokens);
  } catch (err) {
    console.error('Registration error:', err.message);
    res.status(500).json({ error: 'Registration failed' });
  }
});

app.post('/api/v1/auth/refresh', async (req, res) => {
  try {
    const { refresh_token } = req.body;
    if (!refresh_token) {
      return res.status(400).json({ error: 'refresh_token is required' });
    }
    const tokens = await deviceSessions.refreshTokens(refresh_token);
    if (!tokens) {
      return res.status(401).json({ error: 'Invalid or expired refresh token' });
    }
    res.json(tokens);
  } catch (err) {
    console.error('Refresh error:', err.message);
    res.status(500).json({ error: 'Refresh failed' });
  }
});

app.use(healthRoutes(llm, sessionManager, memoryManager));
app.use(commandRoutes(commandRouter, authMiddleware));
app.use(memoryRoutes(memoryManager, authMiddleware));
app.use(skillRoutes(memoryManager, authMiddleware));

app.use((err, req, res, next) => {
  console.error('Unhandled error:', err.message);
  res.status(500).json({ status: 'error', message: 'Internal server error' });
});

app.use((req, res) => {
  res.status(404).json({ status: 'error', message: 'Not found' });
});

const server = createServer(app);

const WsMessageSchema = z.discriminatedUnion('type', [
  z.object({ type: z.literal('command'), command: z.string().min(1).max(2000) }),
  z.object({ type: z.literal('ping') }),
]);

const wss = new WebSocketServer({ server, path: '/ws' });
const wsCommandCounts = new Map();
const WS_RATE_LIMIT = 20;

wss.on('connection', async (ws, req) => {
  if (wss.clients.size > CONFIG.wsMaxConnections) {
    ws.close(1013, 'Server full');
    return;
  }

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const token = url.searchParams.get('token');
  const deviceId = url.searchParams.get('device');

  if (!token || !deviceId) {
    ws.close(4001, 'Missing token or device ID');
    return;
  }

  const valid = await deviceSessions.validateToken(deviceId, token);
  if (!valid) {
    ws.close(4001, 'Invalid token');
    return;
  }

  const session = sessionManager.create(deviceId);
  wsCommandCounts.set(ws, { count: 0, resetAt: Date.now() + 60000 });
  console.log(`WebSocket connected: ${deviceId}`);

  ws.on('message', async (raw) => {
    const record = wsCommandCounts.get(ws);
    if (record) {
      const now = Date.now();
      if (now > record.resetAt) {
        record.count = 0;
        record.resetAt = now + 60000;
      }
      record.count++;
      if (record.count > WS_RATE_LIMIT) {
        ws.send(JSON.stringify({ type: 'error', message: 'Rate limit exceeded' }));
        return;
      }
    }

    try {
      const rawJson = JSON.parse(raw.toString());
      const parsed = WsMessageSchema.safeParse(rawJson);
      if (!parsed.success) {
        ws.send(JSON.stringify({ type: 'error', message: 'Invalid message format' }));
        return;
      }

      const msg = parsed.data;
      if (msg.type === 'command') {
        const result = await commandRouter.route(msg.command, session, deviceId);
        ws.send(JSON.stringify({ type: 'response', data: result }));
      } else if (msg.type === 'ping') {
        ws.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));
      }
    } catch (err) {
      console.error('WS message error:', err.message);
      ws.send(JSON.stringify({ type: 'error', message: err.message }));
    }
  });

  ws.on('close', () => {
    wsCommandCounts.delete(ws);
    console.log(`WebSocket disconnected: ${deviceId}`);
  });

  ws.send(JSON.stringify({
    type: 'connected',
    deviceId,
    message: 'JARVIS backend connected',
  }));
});

setInterval(() => {
  const now = Date.now();
  for (const [ip, record] of requestCounts.entries()) {
    if (now > record.resetAt) requestCounts.delete(ip);
  }
}, 60000);

server.listen(CONFIG.port, () => {
  console.log(`JARVIS backend running on port ${CONFIG.port}`);
  console.log(`WebSocket: ws://localhost:${CONFIG.port}/ws`);
  console.log(`Health: http://localhost:${CONFIG.port}/health`);
  console.log(`LLM providers: ${llm.getProviderStatus().map(p => p.name).join(', ') || 'none configured'}`);
});
