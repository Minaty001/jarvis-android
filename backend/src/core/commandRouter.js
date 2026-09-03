import { z } from 'zod';
import { ActionSchema, ActionPlanSchema } from '../actions/actionSchemas.js';
import { ActionPolicy } from '../actions/actionPolicy.js';

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
4. **Communication**: Find contacts, call, send SMS
5. **Web**: Open URLs in browser
6. **Settings**: Toggle WiFi, Bluetooth
7. **Calendar**: Check today's events, search calendar
8. **Sharing**: Share text to other apps
9. **Memory**: Remember facts, preferences, and learned skills

## Response Format
Respond with a JSON plan:
{
  "intent": "brief description",
  "requires_automation": true/false,
  "actions": [
    {
      "type": "open_app|tap|swipe|type|read_screen|press_back|send_sms|make_call|open_url|media_control|wifi|bluetooth|calendar|share",
      "params": {}
    }
  ],
  "llm_queries": ["question1"],
  "response": "What to say to the user"
}

## Action Param Examples
- open_app: {"package": "com.whatsapp"}
- tap: {"text": "Send"}
- type: {"text": "Hello world"}
- swipe: {"direction": "up"}
- send_sms: {"phone": "+1234567890", "message": "Hi"}
- make_call: {"phone": "+1234567890"}
- open_url: {"url": "https://example.com"}
- media_control: {"action": "play|pause|next|previous|volume_up|volume_down"}
- wifi: {"action": "on|off|toggle"}
- bluetooth: {"action": "on|off|toggle"}
- calendar: {"action": "today|search", "query": "..."}
- share: {"text": "content to share"}

## Rules
- Keep actions minimal and efficient
- If unsure, ask for clarification
- Use memory to personalize responses
- Only use action types from the list above`;

function extractJson(content) {
  if (!content || typeof content !== 'string') return null;

  const codeBlockMatch = content.match(/```(?:json)?\s*([\s\S]*?)\s*```/);
  if (codeBlockMatch) {
    try {
      return JSON.parse(codeBlockMatch[1].trim());
    } catch {
      // Fall through to balanced parser
    }
  }

  const firstBrace = content.indexOf('{');
  if (firstBrace === -1) return null;

  let depth = 0;
  let inString = false;
  let escape = false;

  for (let i = firstBrace; i < content.length; i++) {
    const char = content[i];
    if (escape) {
      escape = false;
      continue;
    }
    if (char === '\\') {
      escape = true;
      continue;
    }
    if (char === '"') {
      inString = !inString;
      continue;
    }
    if (!inString) {
      if (char === '{') {
        depth++;
      } else if (char === '}') {
        depth--;
        if (depth === 0) {
          try {
            return JSON.parse(content.slice(firstBrace, i + 1));
          } catch {
            break;
          }
        }
      }
    }
  }

  try {
    const lastBrace = content.lastIndexOf('}');
    if (lastBrace > firstBrace) {
      return JSON.parse(content.slice(firstBrace, lastBrace + 1));
    }
  } catch {
    return null;
  }

  return null;
}

export class CommandRouter {
  constructor(llmOrchestrator, memoryManager) {
    this.llm = llmOrchestrator;
    this.memory = memoryManager;
  }

  async route(command, session, userId, context) {
    if (session?.addMessage) {
      session.addMessage('user', command);
    }

    if (this.memory && userId) {
      try {
        const skill = await this.memory.matchSkill(userId, command);
        if (skill) {
          const rawActions = Array.isArray(skill.action_sequence) ? skill.action_sequence : [];
          const planResult = ActionPlanSchema.safeParse({ actions: rawActions });
          if (planResult.success) {
            let policyRejected = false;
            for (const action of planResult.data.actions) {
              const check = ActionPolicy.validateAction(action);
              if (!check.valid) {
                console.warn(`Skill '${skill.name}' rejected by action policy: ${check.error}`);
                policyRejected = true;
                break;
              }
            }
            if (!policyRejected) {
              if (session?.addMessage) {
                session.addMessage('assistant', `Executing skill: ${skill.name}`);
              }
              return {
                intent: `skill:${skill.name}`,
                requires_automation: true,
                actions: planResult.data.actions,
                response: `Running "${skill.name}"...`,
                skillMatch: true,
                matchType: skill.matchType,
              };
            }
          }
        }
      } catch (err) {
        console.error('Skill match failed:', err.message);
      }
    }

    let memoryContext = '';
    if (this.memory && userId) {
      try {
        const memories = await this.memory.search(userId, command);
        if (memories && memories.length > 0) {
          memoryContext = '\n\nRelevant memories:\n' +
            memories.map(m => `- ${m.content}`).join('\n');
        }
      } catch (err) {
        console.error('Memory search failed:', err.message);
      }
    }

    const sessionMessages = session?.getMessages ? session.getMessages() : [];
    const messages = [
      { role: 'system', content: SYSTEM_PROMPT + memoryContext },
      ...sessionMessages,
    ];

    const result = await this.llm.generate(messages);

    let parsed;
    try {
      const content = result.content;
      const raw = extractJson(content);
      if (!raw) {
        parsed = { intent: 'direct_response', response: content, actions: [] };
      } else {
        const planResult = ActionPlanSchema.safeParse({ actions: raw.actions || [] });
        if (!planResult.success) {
          console.warn('Action plan validation failed:', planResult.error.errors.map(e => e.message).join('; '));
          parsed = {
            intent: 'unknown',
            response: raw.response || content,
            actions: [],
            requires_automation: false,
          };
        } else {
          let policyRejected = false;
          for (const action of planResult.data.actions) {
            const policyCheck = ActionPolicy.validateAction(action);
            if (!policyCheck.valid) {
              console.warn(`Action policy rejected: ${policyCheck.error}`);
              parsed = {
                intent: 'unknown',
                response: `I can't do that: ${policyCheck.error}`,
                actions: [],
                requires_automation: false,
              };
              policyRejected = true;
              break;
            }
          }

          if (!policyRejected) {
            parsed = {
              intent: raw.intent || 'unknown',
              requires_automation: raw.requires_automation || false,
              actions: planResult.data.actions,
              response: raw.response || '',
            };
          }
        }
      }
    } catch {
      parsed = { intent: 'direct_response', response: result.content, actions: [] };
    }

    if (session?.addMessage) {
      session.addMessage('assistant', parsed.response || '');
    }

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
