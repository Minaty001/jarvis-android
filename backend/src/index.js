import express from 'express';
import cors from 'cors';
import { WebSocketServer } from 'ws';
import { createServer } from 'http';
import { createClient } from '@supabase/supabase-js';
import { z } from 'zod';
import { CONFIG } from './config.js';
import { LLMOrchestrator } from './core/llmOrchestrator.js';
import { SessionManager } from './core/sessionManager.js';
import { CommandRouter } from './core/commandRouter.js';
import { MemoryManager } from './core/memoryManager.js';
import { TokenService } from './auth/tokenService.js';
import { SessionService } from './auth/sessionService.js';
import { EnrollmentService } from './auth/enrollmentService.js';
import { WebSocketAuth } from './auth/websocketAuth.js';
import { createAuthMiddleware } from './middleware/auth.js';
import { createRateLimitMiddleware, createEndpointRateLimit, RATE_LIMITS } from './middleware/rateLimit.js';
import { healthRoutes } from './routes/health.js';
import { commandRoutes } from './routes/command.js';
import { memoryRoutes } from './routes/memory.js';
import { skillRoutes } from './routes/skill.js';
import { createAuthRoutes } from './routes/auth.js';
import { WsTicketStore } from './auth/wsTicketStore.js';

const llm = new LLMOrchestrator();
const sessionManager = new SessionManager();
const memoryManager = new MemoryManager();

const supabase = CONFIG.supabaseUrl ? createClient(CONFIG.supabaseUrl, CONFIG.supabaseKey) : null;
const tokenService = supabase ? new TokenService(supabase) : null;
const sessionService = supabase ? new SessionService(supabase) : null;
const enrollmentService = supabase ? new EnrollmentService(supabase) : null;
const wsTicketStore = new WsTicketStore();
const websocketAuth = tokenService ? new WebSocketAuth(tokenService, wsTicketStore) : null;

const app = express();

const allowedOrigins = (CONFIG.allowedOrigins || '').split(',').filter(Boolean);
app.use(cors({
  origin: allowedOrigins.length > 0 ? allowedOrigins : '*',
  credentials: true,
}));
app.use(express.json({ limit: '1mb' }));
app.use(createRateLimitMiddleware(100, 60000));

app.set('sessionManager', sessionManager);

const commandRouter = new CommandRouter(llm, memoryManager);
const authMiddleware = tokenService ? createAuthMiddleware(tokenService) : null;

if (tokenService) {
  app.use('/api/v1/auth', createAuthRoutes(tokenService, enrollmentService, sessionService, wsTicketStore));
}

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

if (websocketAuth) {
  const wss = new WebSocketServer({ server, path: '/ws' });
  wss.on('connection', (ws, req) => websocketAuth.handleConnection(ws, req));
}

setInterval(() => {
  if (websocketAuth) {
    websocketAuth.cleanupStaleConnections();
  }
}, 60000);

if (sessionService) {
  setInterval(async () => {
    try {
      await sessionService.cleanupExpiredSessions();
    } catch (e) {
      console.error("Session cleanup failed:", e.message);
    }
  }, 3600000);
}

server.listen(CONFIG.port, () => {
  console.log(`JARVIS backend running on port ${CONFIG.port}`);
  console.log(`WebSocket: ws://localhost:${CONFIG.port}/ws`);
  console.log(`Health: http://localhost:${CONFIG.port}/health`);
});