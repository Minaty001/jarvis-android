import express from 'express';
import cors from 'cors';
import { WebSocketServer } from 'ws';
import { createServer } from 'http';
import { CONFIG } from './config.js';
import { LLMOrchestrator } from './core/llmOrchestrator.js';
import { SessionManager } from './core/sessionManager.js';
import { CommandRouter } from './core/commandRouter.js';
import { MemoryManager } from './core/memoryManager.js';
import { healthRoutes } from './routes/health.js';
import { commandRoutes } from './routes/command.js';
import { memoryRoutes } from './routes/memory.js';
import { skillRoutes } from './routes/skill.js';
import { sanitizeMiddleware } from './middleware/sanitize.js';

const llm = new LLMOrchestrator();
const sessionManager = new SessionManager();
const memoryManager = new MemoryManager();
const commandRouter = new CommandRouter(llm, memoryManager);

const app = express();
app.use(cors());
app.use(express.json({ limit: '1mb' }));
app.use(sanitizeMiddleware);
app.set('sessionManager', sessionManager);

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
    return res.status(429).json({ status: 'error', message: 'Rate limit exceeded. Try again later.' });
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

app.use(healthRoutes(llm, sessionManager, memoryManager));
app.use(commandRoutes(commandRouter));
app.use(memoryRoutes(memoryManager));
app.use(skillRoutes(memoryManager));

app.use((err, req, res, next) => {
  console.error('Unhandled error:', err.message);
  res.status(500).json({ status: 'error', message: 'Internal server error' });
});

app.use((req, res) => {
  res.status(404).json({ status: 'error', message: 'Not found' });
});

const server = createServer(app);

const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', (ws, req) => {
  if (wss.clients.size > CONFIG.wsMaxConnections) {
    ws.close(1013, 'Server full');
    return;
  }

  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const deviceId = url.searchParams.get('device') || `ws-${Date.now()}`;
  const session = sessionManager.create(deviceId);

  console.log(`WebSocket connected: ${deviceId}`);

  ws.on('message', async (raw) => {
    try {
      const msg = JSON.parse(raw.toString());

      if (msg.type === 'command') {
        const result = await commandRouter.route(msg.command, session, msg.userId);
        ws.send(JSON.stringify({ type: 'response', data: result }));
      } else if (msg.type === 'ping') {
        ws.send(JSON.stringify({ type: 'pong', timestamp: Date.now() }));
      } else if (msg.type === 'audio') {
        ws.send(JSON.stringify({
          type: 'response',
          data: {
            intent: 'audio_received',
            response: 'Audio processing not yet implemented',
            actions: [],
          },
        }));
      }
    } catch (err) {
      console.error('WS message error:', err.message);
      ws.send(JSON.stringify({ type: 'error', message: err.message }));
    }
  });

  ws.on('close', () => {
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
