import { z } from 'zod';
import { ActionSchema } from './actionSchemas.js';

// ==================== CLIENT → SERVER ====================

const ClientCommandSchema = z.object({
  type: z.literal('command'),
  command: z.string().min(1).max(500),
  requestId: z.string().uuid().optional(),
}).strict();

const ClientPingSchema = z.object({
  type: z.literal('ping'),
  timestamp: z.number().optional(),
}).strict();

const ClientMessageSchema = z.discriminatedUnion('type', [
  ClientCommandSchema,
  ClientPingSchema,
]);

// ==================== SERVER → CLIENT ====================

const ServerCommandResponseSchema = z.object({
  type: z.literal('command_response'),
  requestId: z.string().uuid().optional(),
  intent: z.string().optional().default('unknown'),
  response: z.string().max(5000).optional().default(''),
  actions: z.array(ActionSchema).max(10).optional().default([]),
  provider: z.string().optional(),
  timestamp: z.number().optional(),
}).strict();

const ServerErrorSchema = z.object({
  type: z.literal('error'),
  requestId: z.string().uuid().optional(),
  code: z.number().int(),
  message: z.string().max(1000),
  retryable: z.boolean().optional().default(false),
}).strict();

const ServerPongSchema = z.object({
  type: z.literal('pong'),
  timestamp: z.number(),
}).strict();

const ServerAuthRequiredSchema = z.object({
  type: z.literal('auth_required'),
  message: z.string().max(500),
}).strict();

const ServerEventSchema = z.discriminatedUnion('type', [
  ServerCommandResponseSchema,
  ServerErrorSchema,
  ServerPongSchema,
  ServerAuthRequiredSchema,
]);

// ==================== VALIDATION ====================

export function validateClientMessage(raw) {
  const result = ClientMessageSchema.safeParse(raw);
  if (!result.success) {
    return {
      valid: false,
      error: result.error.issues.map(i => `${i.path.join('.')}: ${i.message}`).join('; '),
    };
  }
  return { valid: true, data: result.data };
}

export function validateServerMessage(raw) {
  const result = ServerEventSchema.safeParse(raw);
  if (!result.success) {
    return {
      valid: false,
      error: result.error.issues.map(i => `${i.path.join('.')}: ${i.message}`).join('; '),
    };
  }
  return { valid: true, data: result.data };
}

export function createCommandResponse({ requestId, intent, response, actions, provider }) {
  return {
    type: 'command_response',
    requestId,
    intent: intent || 'unknown',
    response: response || '',
    actions: actions || [],
    provider,
    timestamp: Date.now(),
  };
}

export function createErrorResponse({ requestId, code, message, retryable = false }) {
  return {
    type: 'error',
    requestId,
    code,
    message,
    retryable,
    timestamp: Date.now(),
  };
}

export function createPong(timestamp) {
  return {
    type: 'pong',
    timestamp: timestamp || Date.now(),
  };
}

export function createAuthRequired(message = 'Authentication required') {
  return {
    type: 'auth_required',
    message,
    timestamp: Date.now(),
  };
}

export {
  ClientCommandSchema,
  ClientPingSchema,
  ClientMessageSchema,
  ServerCommandResponseSchema,
  ServerErrorSchema,
  ServerPongSchema,
  ServerAuthRequiredSchema,
  ServerEventSchema,
};
