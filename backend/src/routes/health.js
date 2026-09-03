import { Router } from 'express';

export function healthRoutes(llmOrchestrator, sessionManager, memoryManager) {
  const router = Router();

  const handleHealth = (req, res) => {
    res.json({
      status: 'alive',
      timestamp: new Date().toISOString(),
      uptime: process.uptime(),
      llm: llmOrchestrator ? llmOrchestrator.getProviderStatus() : { status: 'unknown' },
      sessions: sessionManager ? sessionManager.getStats() : { activeSessions: 0 },
      memory: memoryManager ? memoryManager.getStatus() : { available: false },
    });
  };

  router.get('/health', handleHealth);
  router.get('/api/v1/health', handleHealth);

  return router;
}
