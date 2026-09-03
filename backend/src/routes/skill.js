import { Router } from 'express';
import { z } from 'zod';
import { ActionPolicy } from '../actions/actionPolicy.js';
import { ActionSchema } from '../actions/actionSchemas.js';

const SkillSchema = z.object({
  command: z.string().min(1),
  actions: z.array(ActionSchema),
  name: z.string().optional(),
});

export function skillRoutes(memoryManager, authMiddleware) {
  const router = Router();
  const auth = typeof authMiddleware === 'function' ? authMiddleware : (req, res, next) => next();

  const handleLearn = async (req, res) => {
    try {
      const { command, actions, name } = SkillSchema.parse(req.body);
      const userId = req.authenticatedDeviceId || req.headers['x-device-id'] || 'default-device';

      // Enforce ActionPolicy validation on every action before saving as skill
      for (const action of actions) {
        const policyCheck = ActionPolicy.validateAction(action);
        if (!policyCheck.valid) {
          return res.status(400).json({
            status: 'error',
            message: `Action policy rejected: ${policyCheck.error}`,
          });
        }
      }

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
  };

  const handleMatch = async (req, res) => {
    try {
      const { command } = req.query;
      const userId = req.authenticatedDeviceId || req.query.device_id || req.headers['x-device-id'] || 'default-device';
      if (!command) {
        return res.status(400).json({ status: 'error', message: 'command required' });
      }
      const skill = await memoryManager.matchSkill(userId, command);
      res.json({ skill });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  };

  const handleList = async (req, res) => {
    try {
      const userId = req.authenticatedDeviceId || req.query.device_id || req.headers['x-device-id'] || 'default-device';
      const skills = await memoryManager.listSkills(userId);
      res.json({ status: 'success', skills, results: skills });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  };

  const handleDelete = async (req, res) => {
    try {
      const { id } = req.params;
      const userId = req.authenticatedDeviceId || req.query.device_id || req.headers['x-device-id'] || 'default-device';
      const deleted = await memoryManager.deleteSkill(id, userId);
      if (!deleted) {
        return res.status(404).json({ status: 'error', message: 'Skill not found or access denied' });
      }
      res.json({ status: 'deleted', id });
    } catch (err) {
      res.status(500).json({ status: 'error', message: err.message });
    }
  };

  router.post('/skill/learn', auth, handleLearn);
  router.post('/api/v1/skill/learn', auth, handleLearn);

  router.get('/skill/match', auth, handleMatch);
  router.get('/api/v1/skill/match', auth, handleMatch);

  router.get('/skill/list', auth, handleList);
  router.get('/api/v1/skills', auth, handleList);

  router.delete('/skill/:id', auth, handleDelete);
  router.delete('/api/v1/skills/:id', auth, handleDelete);

  return router;
}
