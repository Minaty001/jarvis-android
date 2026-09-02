# Current Architecture (as of commit 7f802f5)

## Android App

```
MainActivity (thin — observes state)
    ↓
MainViewModel (bridges UI ↔ Runtime)
    ↓
AssistantRuntime (singleton, owns everything)
    ├── AuthManager (TokenState machine)
    ├── ApiClient (ApiResult<T>, OkHttp Authenticator)
    ├── VoiceRuntime (VoiceState machine + MicOwner)
    ├── ConfirmationManager (Activity-independent, 30s timeout)
    ├── ActionPolicyEngine (validate → permission → risk → confirm → execute)
    ├── SkillExecutor (ActionResult sealed interface)
    ├── AutomationController (awaiter patterns)
    ├── ConnectionManager (WS lifecycle)
    └── WebSocketClient (requires AUTHENTICATED)
```

## Auth Flow

```
App start
    ↓
AssistantRuntime.getInstance()
    ↓
AuthManager → loadInitialState() from EncryptedSharedPreferences
    ↓
┌─ Has token? ──── YES ──→ Is expired? ──── NO ──→ AUTHENTICATED
│                         │
│                         YES → Has refresh? ──── YES ──→ REFRESHING → refresh → AUTHENTICATED
│                                        │
│                                        NO → UNAUTHENTICATED
│
NO → REGISTERING → register → save tokens → AUTHENTICATED
    ↓
WebSocket connects (only after AUTHENTICATED)
```

## Voice Pipeline

```
VoiceState.OFF
    ↓ (user enables)
VoiceState.READY
    ↓ (startWakeListening)
VoiceState.WAKE_LISTENING (MicOwner.WAKE)
    ↓ (wake word detected)
VoiceState.COMMAND_LISTENING (MicOwner.COMMAND)
    ↓ (STT result)
VoiceState.PROCESSING
    ↓ (action executed)
VoiceState.SPEAKING (MicOwner.TTS)
    ↓ (TTS complete)
VoiceState.WAKE_LISTENING (if wake enabled)
```

## Action Pipeline

```
LLM → strict schema (18 types) → ActionPlan
    ↓
ActionPolicyEngine.evaluatePlan()
    ├─ validate(): ActionValidator (TypeAction enum + required params)
    ├─ checkPermissions(): ACCESSIBILITY, SMS, etc.
    └─ checkConfirmation(): HIGH risk → ConfirmationManager
    ↓
SkillExecutor.execute()
    └─ returns ActionResult (Success/Failed/ScreenContent/etc.)
```

## Files

### Core
- `AssistantRuntime.kt` — Singleton, owns all runtimes
- `MainViewModel.kt` — AndroidViewModel, observes AssistantRuntime
- `MainActivity.kt` — Thin, only permission handling + setContent

### Auth
- `AuthManager.kt` — TokenState enum, opaque bearer tokens, EncryptedSharedPreferences
- `ApiClient.kt` — ApiResult<T>, OkHttp Authenticator for auto-refresh, bootstrap()

### Voice
- `VoiceRuntime.kt` — VoiceState machine, MicOwner, single mic owner rule
- `OnnxWakeWordDetector.kt` — 3-model ONNX pipeline
- `LiveKitWakeWordEngine.kt` — AudioRecord capture thread

### Automation
- `ActionValidator.kt` — ActionType enum, 18 allowed types, required params
- `ActionPolicyEngine.kt` — validate → permission → confirm → execute pipeline
- `ConfirmationManager.kt` — Activity-independent, with timeout
- `SkillExecutor.kt` — ActionResult sealed interface
- `AutomationController.kt` — awaiter patterns for view detection

### Backend
- `deviceSessionManager.js` — Supabase persistent sessions, token hashing
- `index.js` — DeviceSessionManager, auth middleware, WS Zod validation
- `commandRouter.js` — Strict 18-type action schema
