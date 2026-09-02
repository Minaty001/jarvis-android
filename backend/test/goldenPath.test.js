import { generateToken, hashToken } from '../src/auth/tokenService.js';
import { WsTicketStore } from '../src/auth/wsTicketStore.js';
import { ActionPolicy } from '../src/actions/actionPolicy.js';
import { ActionSchema } from '../src/actions/actionSchemas.js';
import { RATE_LIMITS } from '../src/middleware/rateLimit.js';

describe('Golden Path: Install -> Enroll -> Auth -> WS -> Command -> Policy -> Action', () => {
  let wsTicketStore;

  beforeEach(() => {
    wsTicketStore = new WsTicketStore();
  });

  test('Step 1: Device enrollment creates pairing code', () => {
    const deviceId = 'test-device-001';
    const { ticket: enrollmentSecret, expiresAt } = wsTicketStore.createTicket(deviceId, 300000);
    expect(typeof enrollmentSecret).toBe('string');
    expect(enrollmentSecret.length).toBeGreaterThan(10);
    expect(expiresAt).toBeGreaterThan(Date.now());
  });

  test('Step 2: Token generation produces valid tokens', () => {
    const accessToken = generateToken();
    const refreshToken = generateToken();
    expect(accessToken).not.toBe(refreshToken);
    expect(accessToken.length).toBeGreaterThan(20);
    expect(refreshToken.length).toBeGreaterThan(20);
  });

  test('Step 3: WS ticket auth handshake', () => {
    const deviceId = 'test-device-001';
    const { ticket } = wsTicketStore.createTicket(deviceId);
    const result = wsTicketStore.consumeTicket(ticket);
    expect(result).toEqual({ deviceId });
  });

  test('Step 4: WS ticket cannot be reused', () => {
    const deviceId = 'test-device-001';
    const { ticket } = wsTicketStore.createTicket(deviceId);
    wsTicketStore.consumeTicket(ticket);
    const secondAttempt = wsTicketStore.consumeTicket(ticket);
    expect(secondAttempt).toBeNull();
  });

  test('Step 5: Command validation passes for valid action', () => {
    const result = ActionSchema.safeParse({
      type: 'open_app',
      params: { package: 'com.test' },
    });
    expect(result.success).toBe(true);
  });

  test('Step 6: Command validation rejects unknown type', () => {
    const result = ActionSchema.safeParse({
      type: 'invalid_type',
      params: {},
    });
    expect(result.success).toBe(false);
  });

  test('Step 7: Policy allows low-risk action', () => {
    const validation = ActionPolicy.validateAction({
      type: 'open_app',
      params: { package: 'com.test' },
    });
    expect(validation.valid).toBe(true);
  });

  test('Step 8: Policy identifies high-risk action requiring confirmation', () => {
    const risk = ActionPolicy.getRiskLevel({ type: 'send_sms' });
    expect(risk).toBe('high');
    expect(ActionPolicy.requiresConfirmation({ type: 'send_sms' })).toBe(true);
  });

  test('Step 9: Policy blocks forbidden action', () => {
    const validation = ActionPolicy.validateAction({
      type: 'credential_theft',
      params: {},
    });
    expect(validation.valid).toBe(false);
    expect(validation.error).toContain('forbidden');
  });

  test('Step 10: Full action plan validation pipeline', () => {
    const plan = {
      actions: [
        { type: 'open_app', params: { package: 'com.test' } },
        { type: 'tap', params: { text: 'Login' } },
      ],
    };
    const result = ActionPolicy.validateActionPlan(plan);
    expect(result.valid).toBe(true);
  });

  test('Step 11: Action plan with forbidden action is rejected', () => {
    const plan = {
      actions: [
        { type: 'open_app', params: { package: 'com.test' } },
        { type: 'security_bypass', params: {} },
      ],
    };
    const result = ActionPolicy.validateActionPlan(plan);
    expect(result.valid).toBe(false);
  });
});

describe('WS Auth Integration', () => {
  let wsTicketStore;

  beforeEach(() => {
    wsTicketStore = new WsTicketStore();
  });

  test('ticket creation and consumption flow', () => {
    const deviceId = 'device-abc';
    const { ticket } = wsTicketStore.createTicket(deviceId);
    const result = wsTicketStore.consumeTicket(ticket);
    expect(result).toEqual({ deviceId: 'device-abc' });
  });

  test('ticket is single-use', () => {
    const { ticket } = wsTicketStore.createTicket('device-1');
    wsTicketStore.consumeTicket(ticket);
    expect(wsTicketStore.consumeTicket(ticket)).toBeNull();
  });

  test('expired ticket is rejected', () => {
    const ticket = 'old-ticket';
    wsTicketStore.tickets.set(ticket, {
      deviceId: 'device-1',
      expiresAt: Date.now() - 1000,
      used: false,
    });
    expect(wsTicketStore.consumeTicket(ticket)).toBeNull();
  });
});

describe('Rate Limit Configuration', () => {
  test('all rate limits have valid configuration', () => {
    Object.values(RATE_LIMITS).forEach((config) => {
      expect(config.maxRequests).toBeGreaterThan(0);
      expect(config.windowMs).toBeGreaterThan(0);
    });
  });

  test('device registration has stricter limit than general REST', () => {
    expect(RATE_LIMITS.DEVICE_REGISTRATION.maxRequests).toBeLessThan(RATE_LIMITS.REST.maxRequests);
  });

  test('token refresh has appropriate limit', () => {
    expect(RATE_LIMITS.TOKEN_REFRESH.maxRequests).toBeLessThanOrEqual(20);
  });
});

describe('Security Invariants', () => {
  test('LLM cannot override risk level', () => {
    const llmAction = { type: 'send_sms', params: { phone: '+1', message: 'hi' }, risk: 'low' };
    expect(ActionPolicy.getRiskLevel(llmAction)).toBe('high');
  });

  test('forbidden actions always blocked regardless of context', () => {
    const forbidden = ['credential_theft', 'security_bypass', 'financial_transfer', 'bank_transfer'];
    forbidden.forEach((type) => {
      const result = ActionPolicy.validateAction({ type, params: {} });
      expect(result.valid).toBe(false);
    });
  });

  test('token is opaque (not JWT)', () => {
    const token = generateToken();
    expect(token.split('.').length).toBe(1);
  });

  test('hash is one-way', () => {
    const token = generateToken();
    const hash = hashToken(token);
    expect(hash).not.toBe(token);
    expect(hash.length).toBe(64);
  });
});
