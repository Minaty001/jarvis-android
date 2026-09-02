import { validateClientMessage, validateServerMessage, createCommandResponse, createErrorResponse, createPong, createAuthRequired } from '../src/actions/wsSchemas.js';

describe('ClientMessage validation', () => {
  test('valid command message', () => {
    const result = validateClientMessage({ type: 'command', command: 'open WhatsApp' });
    expect(result.valid).toBe(true);
    expect(result.data.type).toBe('command');
  });

  test('valid ping message', () => {
    const result = validateClientMessage({ type: 'ping', timestamp: Date.now() });
    expect(result.valid).toBe(true);
    expect(result.data.type).toBe('ping');
  });

  test('rejects empty command', () => {
    const result = validateClientMessage({ type: 'command', command: '' });
    expect(result.valid).toBe(false);
  });

  test('rejects unknown message type', () => {
    const result = validateClientMessage({ type: 'unknown' });
    expect(result.valid).toBe(false);
  });

  test('rejects command > 500 chars', () => {
    const result = validateClientMessage({ type: 'command', command: 'x'.repeat(501) });
    expect(result.valid).toBe(false);
  });
});

describe('ServerMessage validation', () => {
  test('valid command_response', () => {
    const result = validateServerMessage({
      type: 'command_response',
      intent: 'open_app',
      response: 'Opening WhatsApp',
      actions: [{ type: 'open_app', params: { package: 'com.whatsapp' } }],
    });
    expect(result.valid).toBe(true);
  });

  test('valid error', () => {
    const result = validateServerMessage({
      type: 'error',
      code: 4001,
      message: 'Auth rejected',
    });
    expect(result.valid).toBe(true);
    expect(result.data.code).toBe(4001);
  });

  test('valid pong', () => {
    const result = validateServerMessage({ type: 'pong', timestamp: Date.now() });
    expect(result.valid).toBe(true);
  });

  test('valid auth_required', () => {
    const result = validateServerMessage({ type: 'auth_required', message: 'Login' });
    expect(result.valid).toBe(true);
  });

  test('rejects error without code', () => {
    const result = validateServerMessage({ type: 'error', message: 'fail' });
    expect(result.valid).toBe(false);
  });
});

describe('Message builders', () => {
  test('createCommandResponse includes timestamp', () => {
    const msg = createCommandResponse({ intent: 'test', response: 'ok', actions: [] });
    expect(msg.type).toBe('command_response');
    expect(msg.timestamp).toBeDefined();
  });

  test('createErrorResponse includes retryable flag', () => {
    const msg = createErrorResponse({ code: 500, message: 'Server error', retryable: true });
    expect(msg.retryable).toBe(true);
  });

  test('createPong returns current timestamp', () => {
    const msg = createPong();
    expect(msg.type).toBe('pong');
    expect(msg.timestamp).toBeGreaterThan(0);
  });

  test('createAuthRequired has default message', () => {
    const msg = createAuthRequired();
    expect(msg.message).toBe('Authentication required');
  });
});
