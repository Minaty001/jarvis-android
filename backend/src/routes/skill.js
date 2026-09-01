import { Router } from 'express';
import { z } from 'zod';

const SkillSchema = z.object({
  command: z.string().min(1),
  actions: z.array(z.object({
    type: z.string(),
    params: z.record(z.any()).optional().default({}),
  })),
  userId: z.string().min(1),
  name: z.string().optional(),
});

export function skillRoutes(memoryManager) {
  const router = Router();

  router.post('/skill/learn', async (req, res) => {
    try {
      const { command, actions, userId, name } = SkillSchema.parse(req.body);
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

  router.get('/skill/match', async (req, res) => {
    try {
      const { userId, command } = req.query;
      if (!userId || !command) {
        return res.status(400).json({ status: 'error', message: 'userId and command required' });
      }
      const skill = await memoryManager.matchSkill(userId, command);
      res.json({ skill });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.get('/skill/list', async (req, res) => {
    try {
      const userId = req.query.userId;
      if (!userId) return res.status(400).json({ status: 'error', message: 'userId required' });
      const skills = await memoryManager.listSkills(userId);
      res.json({ skills });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  router.delete('/skill/:id', async (req, res) => {
    try {
      const { id } = req.params;
      await memoryManager.deleteSkill(id);
      res.json({ status: 'deleted', id });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  });

  return router;
}
