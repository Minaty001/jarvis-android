import { Router } from 'express';
import { z } from 'zod';

const CommandSchema = z.object({
  command: z.string().min(1).max(2000),
  context: z.record(z.unknown()).optional().default({}),
});

export function commandRoutes(commandRouter, authMiddleware) {
  const router = Router();
  const auth = typeof authMiddleware === 'function' ? authMiddleware : (req, res, next) => next();

  const handleCommand = async (req, res) => {
    try {
      const { command, context } = CommandSchema.parse(req.body);
      const deviceId = req.authenticatedDeviceId || req.headers['x-device-id'] || 'default-device';
      const sessionManager = req.app.get('sessionManager');
      const session = sessionManager ? sessionManager.create(deviceId) : null;
      const result = await commandRouter.route(command, session, deviceId, context);
      res.json({
        status: 'success',
        result,
        intent: result.intent,
        response: result.response,
        actions: result.actions,
        data: result
      });
    } catch (err) {
      if (err instanceof z.ZodError) {
        return res.status(400).json({ status: 'error', errors: err.errors });
      }
      console.error('Command error:', err.message);
      res.status(500).json({ status: 'error', message: err.message });
    }
  };

  router.post('/command', auth, handleCommand);
  router.post('/api/v1/command', auth, handleCommand);

  return router;
}
