export function createRateLimitMiddleware(maxRequests = 100, windowMs = 60000) {
  const requests = new Map();

  return (req, res, next) => {
    const ip = req.ip || req.connection.remoteAddress || "unknown";
    const now = Date.now();
    const record = requests.get(ip) || { count: 0, resetAt: now + windowMs };

    if (now > record.resetAt) {
      record.count = 0;
      record.resetAt = now + windowMs;
    }

    record.count++;
    requests.set(ip, record);

    if (record.count > maxRequests) {
      return res.status(429).json({ error: "Rate limit exceeded" });
    }

    res.setHeader("X-RateLimit-Remaining", Math.max(0, maxRequests - record.count));
    next();
  };
}

// Per-endpoint rate limiters with different thresholds
export const RATE_LIMITS = {
  REST: { maxRequests: 100, windowMs: 60_000 },       // 100 req/min
  WEBSOCKET: { maxRequests: 60, windowMs: 60_000 },    // 60 msg/min
  LLM: { maxRequests: 20, windowMs: 60_000 },          // 20 LLM calls/min
  DEVICE_REGISTRATION: { maxRequests: 5, windowMs: 300_000 }, // 5 per 5 min
  TOKEN_REFRESH: { maxRequests: 10, windowMs: 60_000 }, // 10 per min
};

export function createEndpointRateLimit(config) {
  const requests = new Map();

  return (req, res, next) => {
    const key = req.ip || req.connection.remoteAddress || "unknown";
    const now = Date.now();
    const record = requests.get(key) || { count: 0, resetAt: now + config.windowMs };

    if (now > record.resetAt) {
      record.count = 0;
      record.resetAt = now + config.windowMs;
    }

    record.count++;
    requests.set(key, record);

    if (record.count > config.maxRequests) {
      return res.status(429).json({
        error: "Rate limit exceeded",
        retryAfter: Math.ceil((record.resetAt - now) / 1000),
      });
    }

    res.setHeader("X-RateLimit-Limit", config.maxRequests);
    res.setHeader("X-RateLimit-Remaining", Math.max(0, config.maxRequests - record.count));
    res.setHeader("X-RateLimit-Reset", Math.ceil(record.resetAt / 1000));
    next();
  };
}

// Cleanup stale entries every 5 minutes
export function startRateLimitCleanup(rateLimiters, intervalMs = 300_000) {
  return setInterval(() => {
    const now = Date.now();
    for (const limiter of rateLimiters) {
      for (const [key, record] of limiter.entries()) {
        if (now > record.resetAt) {
          limiter.delete(key);
        }
      }
    }
  }, intervalMs);
}
