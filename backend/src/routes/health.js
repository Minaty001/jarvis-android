import { Router } from 'express';

export function healthRoutes(llmOrchestrator, sessionManager, memoryManager) {
  const router = Router();

  router.get('/health', (req, res) => {
    res.json({
      status: 'alive',
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      llm: llmOrchestrator.getProviderStatus(),
      sessions: sessionManager.getStats(),
      memory: memoryManager.getStatus(),
    });
  });

  return router;
}
