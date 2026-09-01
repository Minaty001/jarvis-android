import { Router } from 'express';
import { z } from 'zod';

const SearchSchema = z.object({
  query: z.string().min(1),
  userId: z.string().min(1),
  limit: z.coerce.number().int().min(1).max(20).optional().default(5),
});

const StoreSchema = z.object({
  userId: z.string().min(1),
  content: z.string().min(1).max(5000),
  memoryType: z.enum(['fact', 'skill', 'preference', 'conversation']).default('conversation'),
  importance: z.number().min(0).max(1).optional().default(0.5),
});

export function memoryRoutes(memoryManager) {
  const router = Router();

  router.get('/memory/search', async (req, res) => {
    try {
      const { query, userId, limit } = SearchSchema.parse(req.query);
      const results = await memoryManager.search(userId, query, limit);
      res.json({ results });
    } catch (err) {
      if (err instanceof z.ZodError) {
        return res.status(400).json({ status: 'error', errors: err.errors });
      }
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.get('/memory/recent', async (req, res) => {
    try {
      const userId = req.query.userId;
      const limit = parseInt(req.query.limit || '10', 10);
      if (!userId) return res.status(400).json({ status: 'error', message: 'userId required' });
      const results = await memoryManager.getRecentMemories(userId, limit);
      res.json({ results });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.post('/memory/store', async (req, res) => {
    try {
      const { userId, content, memoryType, importance } = StoreSchema.parse(req.body);
      const memory = await memoryManager.store(userId, content, memoryType, importance);
      res.json({ status: 'stored', memory });
    } catch (err) {
      if (err instanceof z.ZodError) {
        return res.status(400).json({ status: 'error', errors: err.errors });
      }
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.delete('/memory/:id', async (req, res) => {
    try {
      const { id } = req.params;
      if (!memoryManager.available) {
        return res.status(503).json({ status: 'error', message: 'Memory not available' });
      }
      await memoryManager.deleteMemory(id);
      res.json({ status: 'deleted', id });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.get('/memory/stats', async (req, res) => {
    try {
      const userId = req.query.userId;
      if (!userId) return res.status(400).json({ status: 'error', message: 'userId required' });
      const stats = await memoryManager.getStats(userId);
      res.json(stats);
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  return router;
}
