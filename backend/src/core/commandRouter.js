import { z } from 'zod';

const ActionSchema = z.object({
  type: z.string().min(1),
  params: z.record(z.any()).optional().default({}),
});

const LLMOutputSchema = z.object({
  intent: z.string().optional().default('unknown'),
  requires_automation: z.boolean().optional().default(false),
  actions: z.array(ActionSchema).optional().default([]),
  llm_queries: z.array(z.string()).optional().default([]),
  response: z.string().optional().default(''),
}).passthrough();

const SYSTEM_PROMPT = `You are JARVIS, an AI assistant for Android that helps users automate tasks on their phone.

## Your Abilities
1. **App Control**: Open, close, and interact with any installed app
2. **UI Automation**: Tap, swipe, type, read screen content
3. **Media Control**: Play, pause, skip, get current media info
4. **File Management**: Read, write, copy, rename, share files
5. **Communication**: Find contacts, call, send WhatsApp messages/media
6. **Web Search**: Open Chrome, search, read and analyze headlines
7. **Navigation**: Open Google Maps, set destinations
8. **Reminders**: Set, view, and manage reminders
9. **Memory**: Remember facts, preferences, and learned skills

## Response Format
Respond with a JSON plan:
{
  "intent": "brief description",
  "requires_automation": true/false,
  "actions": [
    {
      "type": "open_app|tap|swipe|type|read_screen|wait|...",
      "params": {}
    }
  ],
  "llm_queries": ["question1"],
  "response": "What to say to the user"
}

## Rules
- Keep actions minimal and efficient
- If unsure, ask for clarification
- Use memory to personalize responses`;

export class CommandRouter {
  constructor(llmOrchestrator, memoryManager) {
    this.llm = llmOrchestrator;
    this.memory = memoryManager;
  }

  async route(command, session, userId, context) {
    session.addMessage('user', command);

    // STEP 1: Check for matching learned skill BEFORE calling LLM
    if (this.memory && userId) {
      try {
        const skill = await this.memory.matchSkill(userId, command);
        if (skill) {
          session.addMessage('assistant', `Executing skill: ${skill.name}`);
          return {
            intent: `skill:${skill.name}`,
            requires_automation: true,
            actions: skill.action_sequence,
            response: `Running "${skill.name}"...`,
            skillMatch: true,
            matchType: skill.matchType,
          };
        }
      } catch (err) {
        console.error('Skill match failed:', err.message);
      }
    }

    // STEP 2: Search memories for relevant context
    let memoryContext = '';
    if (this.memory && userId) {
      try {
        const memories = await this.memory.search(userId, command);
        if (memories.length > 0) {
          memoryContext = '\n\nRelevant memories:\n' +
            memories.map(m => `- ${m.content}`).join('\n');
        }
      } catch (err) {
        console.error('Memory search failed:', err.message);
      }
    }

    // STEP 3: Call LLM with memory context
    const messages = [
      { role: 'system', content: SYSTEM_PROMPT + memoryContext },
      ...session.getMessages(),
    ];

    const result = await this.llm.generate(messages);

    // STEP 4: Parse and validate JSON response
    let parsed;
    try {
      const content = result.content;
      const jsonMatch = content.match(/\{[\s\S]*\}/);
      if (!jsonMatch) {
        parsed = { intent: 'direct_response', response: content, actions: [] };
      } else {
        const raw = JSON.parse(jsonMatch[0]);
        const validated = LLMOutputSchema.safeParse(raw);
        if (validated.success) {
          parsed = validated.data;
        } else {
          console.warn('LLM output validation failed:', validated.error.errors);
          parsed = {
            intent: raw.intent || 'unknown',
            response: raw.response || content,
            actions: Array.isArray(raw.actions) ? raw.actions : [],
            requires_automation: false,
          };
        }
      }
    } catch {
      parsed = { intent: 'direct_response', response: result.content, actions: [] };
    }

    // STEP 5: Validate each action in the plan
    const validActions = [];
    for (const action of parsed.actions) {
      if (!action.type || typeof action.type !== 'string') {
        console.warn('Dropping action with missing type:', action);
        continue;
      }
      validActions.push({
        type: action.type,
        params: action.params && typeof action.params === 'object' ? action.params : {},
      });
    }
    parsed.actions = validActions;

    session.addMessage('assistant', parsed.response || '');

    // STEP 6: Store conversation memory
    if (this.memory && userId) {
      try {
        await this.memory.store(userId, `User: ${command}\nJARVIS: ${parsed.response}`, 'conversation');
      } catch (err) {
        console.error('Memory store failed:', err.message);
      }
    }

    return {
      ...parsed,
      provider: result.provider,
    };
  }
}
