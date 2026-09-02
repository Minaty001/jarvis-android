import { Router } from 'express';
import { z } from 'zod';

const CommandSchema = z.object({
  command: z.string().min(1).max(2000),
  context: z.record(z.unknown()).optional().default({}),
});

export function commandRoutes(commandRouter, authMiddleware) {
  const router = Router();

  router.post('/command', authMiddleware, async (req, res) => {
    try {
      const { command, context } = CommandSchema.parse(req.body);
      const deviceId = req.authenticatedDeviceId;
      const session = req.app.get('sessionManager').create(deviceId);
      const result = await commandRouter.route(command, session, deviceId, context);
      res.json({ status: 'success', result });
    } catch (err) {
      if (err instanceof z.ZodError) {
        return res.status(400).json({ status: 'error', errors: err.errors });
      }
      console.error('Command error:', err.message);
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  return router;
}
