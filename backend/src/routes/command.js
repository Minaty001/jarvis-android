import { Router } from 'express';
import { z } from 'zod';

const CommandSchema = z.object({
  command: z.string().min(1).max(2000),
  context: z.object({}).optional().default({}),
  userId: z.string().optional(),
});

export function commandRoutes(commandRouter) {
  const router = Router();

  router.post('/command', async (req, res) => {
    try {
      const { command, context, userId } = CommandSchema.parse(req.body);
      const deviceId = req.headers['x-device-id'] || 'rest-client';
      const session = req.app.get('sessionManager').create(deviceId);
      const result = await commandRouter.route(command, session, userId);
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
