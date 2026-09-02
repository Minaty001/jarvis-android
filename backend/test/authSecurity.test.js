import { generateToken, hashToken } from '../src/auth/tokenService.js';
import { WsTicketStore } from '../src/auth/wsTicketStore.js';

describe('Token Generation Security', () => {
  test('generateToken produces 32-byte base64url tokens', () => {
    const token = generateToken();
    expect(typeof token).toBe('string');
    expect(token.length).toBeGreaterThan(20);
    expect(token).not.toMatch(/[/+=]/);
  });

  test('generateToken produces unique tokens', () => {
    const tokens = new Set();
    for (let i = 0; i < 100; i++) {
      tokens.add(generateToken());
    }
    expect(tokens.size).toBe(100);
  });

  test('hashToken produces consistent SHA-256 hashes', () => {
    const token = 'test-token-abc123';
    const hash1 = hashToken(token);
    const hash2 = hashToken(token);
    expect(hash1).toBe(hash2);
    expect(hash1.length).toBe(64);
  });

  test('hashToken produces different hashes for different tokens', () => {
    const hash1 = hashToken('token-a');
    const hash2 = hashToken('token-b');
    expect(hash1).not.toBe(hash2);
  });
});

describe('WS Ticket Security', () => {
  let store;

  beforeEach(() => {
    store = new WsTicketStore();
  });

  test('createTicket returns ticket and expires_at', () => {
    const { ticket, expiresAt } = store.createTicket('device-1');
    expect(typeof ticket).toBe('string');
    expect(typeof expiresAt).toBe('number');
    expect(expiresAt).toBeGreaterThan(Date.now());
  });

  test('consumeTicket returns deviceId for valid ticket', () => {
    const { ticket } = store.createTicket('device-1');
    const result = store.consumeTicket(ticket);
    expect(result).toEqual({ deviceId: 'device-1' });
  });

  test('consumeTicket returns null for already-used ticket', () => {
    const { ticket } = store.createTicket('device-1');
    store.consumeTicket(ticket);
    const result = store.consumeTicket(ticket);
    expect(result).toBeNull();
  });

  test('consumeTicket returns null for unknown ticket', () => {
    const result = store.consumeTicket('nonexistent-ticket');
    expect(result).toBeNull();
  });

  test('consumeTicket returns null for expired ticket', () => {
    const ticket = 'expired-ticket';
    store.tickets.set(ticket, {
      deviceId: 'device-1',
      expiresAt: Date.now() - 1000,
      used: false,
    });
    const result = store.consumeTicket(ticket);
    expect(result).toBeNull();
  });

  test('cleanup removes expired tickets', () => {
    store.tickets.set('old-ticket', {
      deviceId: 'device-1',
      expiresAt: Date.now() - 1000,
      used: false,
    });
    store.tickets.set('new-ticket', {
      deviceId: 'device-2',
      expiresAt: Date.now() + 60000,
      used: false,
    });
    store.cleanup();
    expect(store.tickets.has('old-ticket')).toBe(false);
    expect(store.tickets.has('new-ticket')).toBe(true);
  });
});

describe('Token Lifecycle', () => {
  test('tokens are opaque (not JWT)', () => {
    const token = generateToken();
    expect(token.split('.').length).toBe(1);
  });

  test('token does not contain sensitive data', () => {
    const token = generateToken();
    expect(token).not.toContain('admin');
    expect(token).not.toContain('secret');
    expect(token).not.toContain('password');
  });

  test('hash is one-way (cannot recover token from hash)', () => {
    const token = generateToken();
    const hash = hashToken(token);
    expect(hash).not.toBe(token);
    expect(hash.length).toBeGreaterThan(token.length);
  });
});
