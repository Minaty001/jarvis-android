export function authMiddleware(deviceTokens) {
  return (req, res, next) => {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ status: 'error', message: 'Missing or invalid Authorization header' });
    }

    const token = authHeader.slice(7);
    let foundDeviceId = null;

    for (const [deviceId, data] of deviceTokens.entries()) {
      if (data.token === token) {
        foundDeviceId = deviceId;
        break;
      }
    }

    if (!foundDeviceId) {
      return res.status(401).json({ status: 'error', message: 'Invalid token' });
    }

    req.authenticatedDeviceId = foundDeviceId;
    next();
  };
}
