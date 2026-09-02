import { createRateLimitMiddleware, createEndpointRateLimit, RATE_LIMITS } from '../src/middleware/rateLimit.js';

describe('Rate limiters', () => {
  function createMockReq(ip = '127.0.0.1') {
    return { ip, connection: { remoteAddress: ip } };
  }

  function createMockRes() {
    const res = {
      statusCode: null,
      body: null,
      headers: {},
      status(code) { res.statusCode = code; return res; },
      json(data) { res.body = data; return res; },
      setHeader(name, value) { res.headers[name] = value; },
    };
    return res;
  }

  test('allows requests within limit', () => {
    const middleware = createRateLimitMiddleware(3, 60000);
    const req = createMockReq();
    const res = createMockRes();
    let called = false;

    middleware(req, res, () => { called = true; });

    expect(called).toBe(true);
    expect(res.headers['X-RateLimit-Remaining']).toBe(2);
  });

  test('blocks requests over limit', () => {
    const middleware = createRateLimitMiddleware(2, 60000);
    const req = createMockReq();
    const res = createMockRes();

    middleware(req, res, () => {});
    middleware(req, res, () => {});
    middleware(req, res, () => {});

    expect(res.statusCode).toBe(429);
    expect(res.body.error).toBe('Rate limit exceeded');
  });

  test('endpoint rate limits have correct values', () => {
    expect(RATE_LIMITS.REST.maxRequests).toBe(100);
    expect(RATE_LIMITS.WEBSOCKET.maxRequests).toBe(60);
    expect(RATE_LIMITS.LLM.maxRequests).toBe(20);
    expect(RATE_LIMITS.DEVICE_REGISTRATION.maxRequests).toBe(5);
    expect(RATE_LIMITS.TOKEN_REFRESH.maxRequests).toBe(10);
  });

  test('createEndpointRateLimit sets retry header', () => {
    const limiter = createEndpointRateLimit({ maxRequests: 1, windowMs: 60000 });
    const req = createMockReq();
    const res = createMockRes();

    limiter(req, res, () => {});
    limiter(req, res, () => {});

    expect(res.statusCode).toBe(429);
    expect(res.body.retryAfter).toBeGreaterThan(0);
    expect(res.headers['X-RateLimit-Limit']).toBe(1);
    expect(res.headers['X-RateLimit-Remaining']).toBe(0);
    expect(res.headers['X-RateLimit-Reset']).toBeGreaterThan(0);
  });
});
