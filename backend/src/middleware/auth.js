export function createAuthMiddleware(tokenService) {
  return async (req, res, next) => {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith("Bearer ")) {
      return res.status(401).json({ error: "Missing or invalid Authorization header" });
    }

    const token = authHeader.slice(7);
    const deviceId = req.headers["x-device-id"] || req.headers["device_id"] || req.query?.device_id;

    if (!deviceId) {
      return res.status(401).json({ error: "Missing X-Device-ID header" });
    }

    if (tokenService) {
      const isValid = await tokenService.validateToken(deviceId, token);
      if (!isValid) {
        return res.status(401).json({ error: "Invalid or expired token" });
      }
    }

    req.authenticatedDeviceId = deviceId;
    next();
  };
}