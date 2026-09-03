import { Router } from 'express';
import { z } from 'zod';

const SearchSchema = z.object({
  query: z.string().min(1),
  limit: z.coerce.number().int().min(1).max(20).optional().default(5),
});

const StoreSchema = z.object({
  content: z.string().min(1).max(5000),
  memoryType: z.enum(['fact', 'skill', 'preference', 'conversation']).optional(),
  memory_type: z.enum(['fact', 'skill', 'preference', 'conversation']).optional(),
  importance: z.number().min(0).max(1).optional().default(0.5),
});

export function memoryRoutes(memoryManager, authMiddleware) {
  const router = Router();
  const auth = typeof authMiddleware === 'function' ? authMiddleware : (req, res, next) => next();

  const handleSearch = async (req, res) => {
    try {
      const source = req.method === 'POST' ? req.body : req.query;
      const { query, limit } = SearchSchema.parse(source);
      const userId = req.authenticatedDeviceId || req.query?.device_id || req.body?.device_id || req.body?.user_id || req.headers['x-device-id'] || 'default-device';
      const results = await memoryManager.search(userId, query, limit);
      res.json({ results, memories: results });
    } catch (err) {
      if (err instanceof z.ZodError) {
        return res.status(400).json({ status: 'error', errors: err.errors });
      }
      res.status(500).json({ status: 'error', message: err.message });
    }
  };

  const handleRecent = async (req, res) => {
    try {
      const userId = req.authenticatedDeviceId || req.query?.device_id || req.headers['x-device-id'] || 'default-device';
      const limit = parseInt(req.query.limit || '10', 10);
      const results = await memoryManager.getRecentMemories(userId, limit);
      res.json({ results, memories: results });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  };

  const handleStore = async (req, res) => {
    try {
      const { content, memoryType, memory_type, importance } = StoreSchema.parse(req.body);
      const effectiveType = memoryType || memory_type || 'conversation';
      const userId = req.authenticatedDeviceId || req.body?.device_id || req.body?.user_id || req.headers['x-device-id'] || 'default-device';
      const memory = await memoryManager.store(userId, content, effectiveType, importance);
      res.json({ status: 'stored', memory });
    } catch (err) {
      if (err instanceof z.ZodError) {
        return res.status(400).json({ status: 'error', errors: err.errors });
      }
      res.status(500).json({ status: 'error', message: err.message });
    }
  };

  const handleDelete = async (req, res) => {
    try {
      const { id } = req.params;
      const userId = req.authenticatedDeviceId || req.query?.device_id || req.headers['x-device-id'] || 'default-device';
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
  };

  const handleStats = async (req, res) => {
    try {
      const userId = req.authenticatedDeviceId || req.query?.device_id || req.headers['x-device-id'] || 'default-device';
      const stats = await memoryManager.getStats(userId);
      res.json(stats);
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  };

  router.get('/memory/search', auth, handleSearch);
  router.post('/memory/search', auth, handleSearch);
  router.get('/api/v1/memory/search', auth, handleSearch);
  router.post('/api/v1/memory/search', auth, handleSearch);

  router.get('/memory/recent', auth, handleRecent);
  router.get('/api/v1/memories', auth, handleRecent);

  router.post('/memory/store', auth, handleStore);
  router.post('/api/v1/memories', auth, handleStore);
  router.post('/api/v1/memory/store', auth, handleStore);

  router.delete('/memory/:id', auth, handleDelete);
  router.delete('/api/v1/memories/:id', auth, handleDelete);

  router.get('/memory/stats', auth, handleStats);
  router.get('/api/v1/memory/stats', auth, handleStats);

  return router;
}
