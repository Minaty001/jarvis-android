import { describe, test, expect, jest } from '@jest/globals';
import express from 'express';
import { commandRoutes } from '../src/routes/command.js';
import { memoryRoutes } from '../src/routes/memory.js';
import { skillRoutes } from '../src/routes/skill.js';

describe('Route Factories', () => {
  const mockLLM = { getProviderStatus: () => ({ status: 'ok' }) };
  const mockSessionManager = { getStats: () => ({ activeSessions: 0 }), create: () => ({}) };
  const mockMemoryManager = { getStatus: () => ({ available: false }) };
  const mockCommandRouter = { route: jest.fn() };

  test('commandRoutes can be instantiated without authMiddleware', () => {
    expect(() => {
      const router = commandRoutes(mockCommandRouter);
      const app = express();
      app.use(router);
    }).not.toThrow();
  });

  test('memoryRoutes can be instantiated without authMiddleware', () => {
    expect(() => {
      const router = memoryRoutes(mockMemoryManager);
      const app = express();
      app.use(router);
    }).not.toThrow();
  });

  test('skillRoutes can be instantiated without authMiddleware', () => {
    expect(() => {
      const router = skillRoutes(mockMemoryManager);
      const app = express();
      app.use(router);
    }).not.toThrow();
  });

  test('commandRoutes can be instantiated with authMiddleware function', () => {
    const dummyAuth = (req, res, next) => next();
    expect(() => {
      const router = commandRoutes(mockCommandRouter, dummyAuth);
      const app = express();
      app.use(router);
    }).not.toThrow();
  });
});
