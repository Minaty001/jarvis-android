# Current Architecture (as of commit 89087bf)

## Android App

```
MainActivity (god object)
 ├── Voice (ONNX wake word + STT + TTS)
 ├── WebSocket (OkHttp)
 ├── Automation (AccessibilityService + controllers)
 ├── Confirmation (Compose dialog)
 ├── Memory (Room DB + WorkManager sync)
 └── UI (Compose screens)
```

### Problems
- MainActivity owns all lifecycle (433 lines)
- Voice state tracked via `wakeWordEnabled: Boolean` + `voiceMode: VoiceInputMode`
- ONNX models loaded in OnnxWakeWordDetector constructor (async via coroutine)
- ClapDetector removed from init but not fully architected out
- WebSocket created inside Composable `remember {}` block
- Auth state not driving connection lifecycle
- AutomationController eagerly constructs all subsystems
- Thread.sleep replaced with retry loops but no proper awaiter pattern
- Boolean results everywhere (no ActionResult type)

### Files
- `MainActivity.kt` — 433 lines, contains Activity + Composables
- `AuthTokenManager.kt` — Opaque token with stored expiry timestamp
- `WebSocketClient.kt` — OkHttp WebSocket with reconnect
- `ApiClient.kt` — REST client with manual auth headers
- `AutomationController.kt` — All automation methods
- `SkillExecutor.kt` — Action execution with ConfirmationGate
- `ActionValidator.kt` — Risk classification + param validation
- `OnnxWakeWordDetector.kt` — 3-model ONNX pipeline
- `LiveKitWakeWordEngine.kt` — AudioRecord capture thread

## Backend

```
Node.js/Express
 ├── Auth (in-memory deviceTokens Map)
 ├── LLM (Groq → OpenRouter → NVIDIA NIM)
 ├── Memory (Supabase pgvector / keyword fallback)
 ├── Skills (3-tier matching)
 └── WebSocket (token verified on connect)
```

### Problems
- Tokens stored in process memory (lost on restart)
- No persistent device/session storage
- Auth middleware O(n) scan
- WS messages Zod-validated but REST auth is manual
- LLM output validated with strict schema (good)
- No ownership enforcement at DB level (no RLS)

## Current Auth Flow

```
App start
    ↓
Config.getDeviceId() → UUID
    ↓
AuthTokenManager → null (fresh install)
    ↓
NO REGISTRATION HAPPENS
    ↓
WebSocket connects (no token)
    ↓
Backend rejects (4001)
```

## Current Action Pipeline

```
LLM → strict schema (18 types) → Android ActionValidator → SkillExecutor → execute
```

## Current Voice Pipeline

```
ONNX wake → pause wake → STT → send command → resume wake
```
