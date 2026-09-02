import { z } from 'zod';

const ActionParams = {
  open_app: z.object({ package: z.string().min(1) }),
  tap: z.object({ text: z.string().min(1) }),
  type: z.object({ text: z.string().min(1) }),
  swipe: z.object({ direction: z.enum(['up', 'down', 'left', 'right']).optional().default('up') }),
  wait: z.object({ durationMs: z.number().int().min(0).max(30000).optional().default(1000) }),
  go_back: z.object({}).optional().default({}),
  go_home: z.object({}).optional().default({}),
  read_screen: z.object({}).optional().default({}),
  send_sms: z.object({ phone: z.string().min(1), message: z.string().min(1) }),
  share_text: z.object({ text: z.string().min(1) }),
  bluetooth_on: z.object({}).optional().default({}),
  bluetooth_off: z.object({}).optional().default({}),
  bluetooth_toggle: z.object({}).optional().default({}),
  wifi_on: z.object({}).optional().default({}),
  wifi_off: z.object({}).optional().default({}),
  wifi_toggle: z.object({}).optional().default({}),
  battery_status: z.object({}).optional().default({}),
  calendar_today: z.object({}).optional().default({}),
  calendar_search: z.object({ query: z.string().min(1) }),
};

const ALLOWEDActionTypes = Object.keys(ActionParams);

const ActionSchema = z.object({
  type: z.enum(ALLOWEDActionTypes),
  params: z.record(z.any()).optional().default({}),
}).superRefine((action, ctx) => {
  const paramSchema = ActionParams[action.type];
  if (!paramSchema) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: `Unknown action type: ${action.type}` });
    return;
  }
  const result = paramSchema.safeParse(action.params || {});
  if (!result.success) {
    for (const issue of result.error.issues) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: `Action '${action.type}' param error: ${issue.message}` });
    }
  } else {
    action.params = result.data;
  }
});

const LLMOutputSchema = z.object({
  intent: z.string().optional().default('unknown'),
  requires_automation: z.boolean().optional().default(false),
  actions: z.array(ActionSchema).max(10).optional().default([]),
  llm_queries: z.array(z.string()).max(5).optional().default([]),
  response: z.string().max(2000).optional().default(''),
}).strict();

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
      "type": "open_app|tap|swipe|type|read_screen|wait|go_back|go_home|send_sms|share_text|bluetooth_on|bluetooth_off|bluetooth_toggle|wifi_on|wifi_off|wifi_toggle|battery_status|calendar_today|calendar_search",
      "params": {}
    }
  ],
  "llm_queries": ["question1"],
  "response": "What to say to the user"
}

## Rules
- Keep actions minimal and efficient
- If unsure, ask for clarification
- Use memory to personalize responses
- Only use action types from the list above`;

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
          console.warn('LLM output validation failed:', validated.error.errors.map(e => e.message).join('; '));
          parsed = {
            intent: 'unknown',
            response: raw.response || content,
            actions: [],
            requires_automation: false,
          };
        }
      }
    } catch {
      parsed = { intent: 'direct_response', response: result.content, actions: [] };
    }

    session.addMessage('assistant', parsed.response || '');

    // STEP 5: Store conversation memory
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
