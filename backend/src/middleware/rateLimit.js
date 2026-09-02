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