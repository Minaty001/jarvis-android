# Current Architecture (as of 01-auth-contract)

## Android App

```
MainActivity (thin — observes state, permission handling)
    ↓
MainViewModel (bridges UI ↔ Runtime, uses AuthState)
    ↓
AssistantRuntime (singleton, owns everything)
    ├── AuthManager (AuthState sealed interface, persistent tokens)
    ├── ApiClient (register/refresh, OkHttp)
    ├── ConnectionManager (WS lifecycle, 7 states)
    ├── WebSocketClient (token+deviceId, auth-first connect)
    ├── ConfirmationManager (Activity-independent, 20s timeout → DENY)
    ├── ActionPolicyEngine (validate → permission → risk → confirm → execute)
    ├── SkillExecutor (ActionResult sealed interface)
    ├── AutomationController (awaiter patterns)
    └── WakeEngine / STT / TTS
```

## Auth Flow

```
App start
    ↓
MainViewModel.initialize()
    ↓
AssistantRuntime.bootstrapAndConnect()
    ↓
AuthManager.initialize()
    ↓
load tokens from EncryptedSharedPreferences
    ↓
┌─ Has tokens? ──→ Is access expired? ──→ No ──→ AUTHENTICATED
│                          │
│                          YES → Has refresh? ──→ Yes ──→ REFRESHING → refresh → AUTHENTICATED
│                                        │
│                                        NO → LOGGED_OUT
│
NO → LOGGED_OUT → registerDevice() → save tokens → AUTHENTICATED
    ↓
AssistantRuntime.connectWebSocket()
    ↓
WebSocketClient.connect(token, deviceId)
    ↓
CONNECTED (only if AuthState == AUTHENTICATED)
```

## Auth States

```kotlin
sealed interface AuthState {
    data object Loading : AuthState
    data object Registering : AuthState
    data object Authenticated : AuthState
    data object Refreshing : AuthState
    data object LoggedOut : AuthState
    data class Error(val reason: String) : AuthState
}
```

## Connection States

```kotlin
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    AUTH_FAILED,
    STOPPED
}
```

## Backend Auth

```
POST /api/v1/auth/token
    Body: { device_id, device_name, device_model, os_version }
    Response: { accessToken, refreshToken, expiresIn, deviceId, trusted }

POST /api/v1/auth/refresh
    Body: { refresh_token }
    Response: { accessToken, refreshToken, expiresIn, deviceId, trusted }

WS /ws?device=<deviceId>&token=<token>
    Server validates via TokenService.validateToken()
    → 4001 on failure
```

## Token Model

- Opaque bearer tokens (NOT JWT)
- Generated via `crypto.randomBytes(32).toString("base64url')`
- Stored hashed in Supabase `device_sessions` table
- Raw tokens never stored server-side
- Access: 24h, Refresh: 30 days

## Files

### Auth (NEW — com.jarvis.auth)
- `AuthState.kt` — sealed interface: Loading/Registering/Authenticated/Refreshing/LoggedOut/Error
- `TokenStore.kt` — EncryptedSharedPreferences, 5 keys (access/refresh/deviceId/accessExpiry/refreshExpiry)
- `AuthManager.kt` — state machine, refreshMutex, initialize/register/refresh/logout

### Backend Auth (NEW — backend/src/auth/)
- `tokenService.js` — TokenService class, Supabase-backed, createSession/validateToken/refreshTokens
- `sessionService.js` — SessionService class, getSession/revokeAllSessions/cleanupExpiredSessions
- `enrollmentService.js` — EnrollmentService class, enrollDevice/verifyEnrollment
- `websocketAuth.js` — WebSocketAuth class, handleConnection + Zod validation + stale cleanup

### Core
- `AssistantRuntime.kt` — Singleton, uses com.jarvis.auth.AuthManager, bootstraps auth before WS
- `MainViewModel.kt` — AndroidViewModel, observes AuthState (not old TokenState)
- `MainActivity.kt` — Thin, permission handling + setContent only

### Backend
- `index.js` — imports createClient, wires TokenService/SessionService/WebSocketAuth
- `middleware/auth.js` — createAuthMiddleware (Bearer + X-Device-ID)
- `middleware/rateLimit.js` — createRateLimitMiddleware (IP-based)
- `routes/auth.js` — POST /token, POST /refresh with Zod validation
