# API Contract

## REST Endpoints

### Auth
```
POST /api/v1/auth/token
  Body: { device_id, device_name?, device_model?, os_version? }
  Response: { access_token, refresh_token, expires_in, device_id, trusted }

POST /api/v1/auth/refresh
  Body: { refresh_token }
  Response: { access_token, refresh_token, expires_in, device_id, trusted }
```

### Command
```
POST /command (auth required)
  Body: { command: string, context?: object }
  Response: { status, result: { intent, actions[], response, provider } }
```

### Memory
```
GET  /memory/search?query=...&limit=... (auth required)
GET  /memory/recent?limit=... (auth required)
POST /memory/store (auth required)
DELETE /memory/:id (auth required, ownership enforced)
GET  /memory/stats (auth required)
```

### Skills
```
POST /skill/learn (auth required)
GET  /skill/match?command=... (auth required)
GET  /skill/list (auth required)
DELETE /skill/:id (auth required, ownership enforced)
```

## WebSocket

### Connection
```
ws://host/ws?device={device_id}&token={access_token}
```

### Client → Server
```json
{ "type": "command", "command": "open WhatsApp" }
{ "type": "ping" }
```

### Server → Client
```json
{ "type": "connected", "deviceId": "...", "message": "..." }
{ "type": "response", "data": { "intent": "...", "actions": [...], "response": "..." } }
{ "type": "pong", "timestamp": 12345 }
{ "type": "error", "message": "..." }
```

## Error Codes

| Code | Meaning |
|------|---------|
| 4001 | Missing/invalid token |
| 1013 | Server full |
| 429 | Rate limit |
