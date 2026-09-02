import { Router } from 'express';
import { z } from 'zod';

const SkillSchema = z.object({
  command: z.string().min(1),
  actions: z.array(z.object({
    type: z.string(),
    params: z.record(z.any()).optional().default({}),
  })),
  name: z.string().optional(),
});

export function skillRoutes(memoryManager, authMiddleware) {
  const router = Router();

  router.post('/skill/learn', authMiddleware, async (req, res) => {
    try {
      const { command, actions, name } = SkillSchema.parse(req.body);
      const userId = req.authenticatedDeviceId;
      const skillName = name || command.slice(0, 50);
      const skill = await memoryManager.storeSkill(
        userId,
        skillName,
        command,
        actions,
        [command]
      );
      res.json({ status: 'learned', skill });
    } catch (err) {
      if (err instanceof z.ZodError) {
        return res.status(400).json({ status: 'error', errors: err.errors });
      }
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.get('/skill/match', authMiddleware, async (req, res) => {
    try {
      const { command } = req.query;
      const userId = req.authenticatedDeviceId;
      if (!command) {
        return res.status(400).json({ status: 'error', message: 'command required' });
      }
      const skill = await memoryManager.matchSkill(userId, command);
      res.json({ skill });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.get('/skill/list', authMiddleware, async (req, res) => {
    try {
      const userId = req.authenticatedDeviceId;
      const skills = await memoryManager.listSkills(userId);
      res.json({ skills });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.delete('/skill/:id', authMiddleware, async (req, res) => {
    try {
      const { id } = req.params;
      const userId = req.authenticatedDeviceId;
      const deleted = await memoryManager.deleteSkill(id, userId);
      if (!deleted) {
        return res.status(404).json({ status: 'error', message: 'Skill not found or access denied' });
      }
      res.json({ status: 'deleted', id });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  return router;
}
