# Current Architecture (after commits 01–20)

## Package Structure

### Android (com.jarvis)
```
assistant/          — AssistantRuntime, AssistantState, AssistantEvent
voice/              — VoiceRuntime, VoiceState, MicOwner
audio/              — AudioSessionManager, ClapDetector
auth/               — AuthState, TokenStore, AuthManager
automation/         — AutomationController, ActionPolicyEngine, ConfirmationManager,
                      SkillExecutor, UiAwaiter, JarvisAccessibilityService
accessibility/      — AccessibilityEngine (coroutine wrapper)
backend/            — ApiClient, WebSocketClient, ConnectionManager, WsMessage
memory/             — MemoryManager, CachedMemory, MemoryDao, JarvisDatabase
privacy/            — PrivacyBoundary
skills/             — SkillManager, CachedSkill, SkillDao
wakeword/           — OnnxWakeWordDetector, LiveKitWakeWordEngine, OnnxLifecycleState
tts/                — TtsManager
stt/                — NativeSttManager
config/             — Config
ui/                 — MainActivity, screens, components, viewmodel/MainViewModel
```

### Backend (backend/src/)
```
auth/               — tokenService, sessionService, enrollmentService, websocketAuth
actions/            — actionSchemas, actionRegistry, actionPolicy
core/               — commandRouter, confirmationManager, llmOrchestrator, memoryManager
middleware/         — auth, rateLimit, validate
routes/             — auth, memory, skills, actions
index.js            — Express + WS server
```

## Key Data Flow

```
User speech → VoiceRuntime → STT → AssistantRuntime → WS → Backend
    → CommandRouter → LLM → ActionPlan → ActionPolicy.validate()
    → ConfirmationManager (20s) → SkillExecutor → AutomationController
    → JarvisAccessibilityService → Device
    → Response → TTS → User
```

## Auth Flow

```
App start → AuthManager.initialize() → load tokens
    ├─ Has valid tokens → AUTHENTICATED → WS connect
    ├─ Has expired access → REFRESHING → refresh → AUTHENTICATED
    ├─ No tokens → registerDevice(enrollmentSecret) → save tokens
    └─ Refresh fails → LOGGED_OUT
```

## Action Policy

```
ActionPlan → ActionSchema.validate (Zod discriminated union)
    → ActionPolicy.validateAction (risk check)
    → ConfirmationManager.requestConfirmation (20s timeout → DENY)
    → SkillExecutor.executeAction (type dispatch)
```

## Risk Matrix

| Action        | Risk   | Confirm | Permissions     |
|---------------|--------|---------|-----------------|
| open_app      | LOW    | No      | —               |
| tap/swipe/back| LOW    | No      | accessibility   |
| type          | MEDIUM | No      | accessibility   |
| wifi/bluetooth| MEDIUM | No      | —               |
| send_sms      | HIGH   | Yes     | sms             |
| make_call     | HIGH   | Yes     | phone           |
| bank_transfer | FORBIDDEN | —   | —               |

## Testing

- Android: JUnit unit tests (auth, action policy, privacy)
- Backend: Jest integration tests (action schema, policy, registry)
- Run: `./gradlew test` (Android), `npm test` (backend)
