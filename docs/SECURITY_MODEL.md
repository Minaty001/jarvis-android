# Security Model

## Auth

- Opaque bearer tokens (crypto.randomBytes(32))
- Stored in EncryptedSharedPreferences (Android)
- Stored in device_sessions table (Supabase)
- Token expiry: 24h access, 30d refresh
- Refresh rotation on every use

## Token States

```
NO_TOKEN → REGISTERING → AUTHENTICATED
                              ↓
                         REFRESHING
                              ↓
                         AUTHENTICATED (new tokens)
                              ↓
                         UNAUTHENTICATED (refresh failed)
```

## Authorization

- All REST routes require Bearer token
- userId derived from authenticated device, never from request body
- Memory/skill delete requires ownership match
- WebSocket requires valid token + device ID on connection

## Action Security

- 18 allowed action types (strict enum)
- Per-action required parameter validation
- Risk levels: AUTOMATIC, LOW, MEDIUM, HIGH, FORBIDDEN
- HIGH risk → ConfirmationManager (with timeout)
- FORBIDDEN → always blocked
- Unknown → always blocked

## Privacy

- Screen data = LOCAL by default
- Sensitive app content never sent to cloud
- PrivacyFilter redacts OTP, CVV, PIN, passwords
- read_screen returns structured snapshot, not raw text
