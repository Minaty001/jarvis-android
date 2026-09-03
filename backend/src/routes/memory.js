import { Router } from 'express';
import { z } from 'zod';

const SearchSchema = z.object({
  query: z.string().min(1),
  limit: z.coerce.number().int().min(1).max(20).optional().default(5),
});

const StoreSchema = z.object({
  content: z.string().min(1).max(5000),
  memoryType: z.enum(['fact', 'skill', 'preference', 'conversation']).default('conversation'),
  importance: z.number().min(0).max(1).optional().default(0.5),
});

export function memoryRoutes(memoryManager, authMiddleware) {
  const router = Router();
  const auth = typeof authMiddleware === 'function' ? authMiddleware : (req, res, next) => next();

  router.get('/memory/search', auth, async (req, res) => {
    try {
      const { query, limit } = SearchSchema.parse(req.query);
      const userId = req.authenticatedDeviceId;
      const results = await memoryManager.search(userId, query, limit);
      res.json({ results });
    } catch (err) {
      if (err instanceof z.ZodError) {
        return res.status(400).json({ status: 'error', errors: err.errors });
      }
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.get('/memory/recent', auth, async (req, res) => {
    try {
      const userId = req.authenticatedDeviceId;
      const limit = parseInt(req.query.limit || '10', 10);
      const results = await memoryManager.getRecentMemories(userId, limit);
      res.json({ results });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.post('/memory/store', auth, async (req, res) => {
    try {
      const { content, memoryType, importance } = StoreSchema.parse(req.body);
      const userId = req.authenticatedDeviceId;
      const memory = await memoryManager.store(userId, content, memoryType, importance);
      res.json({ status: 'stored', memory });
    } catch (err) {
      if (err instanceof z.ZodError) {
        return res.status(400).json({ status: 'error', errors: err.errors });
      }
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.delete('/memory/:id', auth, async (req, res) => {
    try {
      const { id } = req.params;
      const userId = req.authenticatedDeviceId;
      if (!memoryManager.available) {
        return res.status(503).json({ status: 'error', message: 'Memory not available' });
      }
      const deleted = await memoryManager.deleteMemory(id, userId);
      if (!deleted) {
        return res.status(404).json({ status: 'error', message: 'Memory not found or access denied' });
      }
      res.json({ status: 'deleted', id });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.get('/memory/stats', auth, async (req, res) => {
    try {
      const userId = req.authenticatedDeviceId;
      const stats = await memoryManager.getStats(userId);
      res.json(stats);
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  return router;
}
