# Current Architecture (after commits 01–38)

## Package Structure

### Android (com.jarvis)
```
assistant/          — AssistantRuntime, AssistantState, AssistantEvent
voice/              — VoiceRuntime, VoiceState, MicOwner
audio/              — AudioSessionManager, ClapDetector
auth/               — AuthState, TokenStore, AuthManager, PairingStore
core/               — PerformanceMonitor (thread-safe bounded ring buffer + p50/p95/p99)
wakeword/           — OnnxWakeWordDetector, LiveKitWakeWordEngine, OnnxLifecycleState
stt/                — SttEngine interface, NativeSttManager
tts/                — TtsEngine interface, TtsManager
backend/            — ApiClient, WebSocketClient, ConnectionManager, WsMessage, ApiResult
automation/         — AutomationController, ActionPolicyEngine, ConfirmationManager,
                      SkillExecutor, UiAwaiter, JarvisAccessibilityService
accessibility/      — AccessibilityEngine (coroutine wrapper)
memory/             — MemoryManager, CachedMemory, MemoryDao, JarvisDatabase
privacy/            — PrivacyBoundary
skills/             — SkillManager, CachedSkill, SkillDao
config/             — Config
ui/                 — MainActivity, screens (Settings, Main), components, viewmodel/MainViewModel
```

### Backend (backend/src/)
```
auth/               — tokenService, sessionService, enrollmentService, websocketAuth, wsTicketStore
actions/            — actionSchemas, actionRegistry, actionPolicy (forbidden types)
core/               — commandRouter, confirmationManager, llmOrchestrator, memoryManager
middleware/         — auth, rateLimit (per-endpoint limits), validate
routes/             — auth (/enroll, /token, /ws-ticket, /refresh), memory, skills, actions
scripts/            — enroll-device.js (admin device seeding)
test/               — 93 tests across 8 suites
index.js            — Express + WS server
```

## Data Flow

```
User speech → VoiceRuntime (AudioSessionManager → mic focus)
    → STT (NativeSttManager) → transcript
    → AssistantRuntime → WS (ticket auth) → Backend
    → CommandRouter → LLM → ActionPlan
    → ActionPolicy.validate (risk check + forbidden block)
    → ConfirmationManager (HIGH/CRITICAL)
    → SkillExecutor (PerformanceMonitor timed)
    → AutomationController → JarvisAccessibilityService → Device
    → Response → TTS → User
    → OnnxWakeWordDetector → listening again
```

## Auth Flow

```
App start → AuthManager.initialize() → load tokens
    ├─ Has valid tokens → AUTHENTICATED → WS connect (ticket)
    ├─ Has expired access → REFRESHING → refresh → AUTHENTICATED
    ├─ No tokens → NeedsEnrollment → Settings "Pair Device" dialog
    │   → enrollment_secret → POST /auth/enroll → tokens saved
    ├─ No tokens (CLI) → enroll-device.js → pairing_code → user enters
    └─ Refresh fails → LOGGED_OUT
```

## WebSocket Auth

```
1. POST /auth/ws-ticket (Bearer token) → single-use 60s ticket
2. WebSocket connect with ticket param (no token in URL)
3. WS ticket stored in wsTicketStore, verified once then deleted
```

## Action Policy

```
ActionPlan → ActionSchema.validate (Zod discriminated union)
    → ActionPolicy.validateAction (risk check + forbidden types)
    → Forbidden types: credential_theft, security_bypass, financial_transfer
    → ConfirmationManager.requestConfirmation (20s timeout → DENY)
    → SkillExecutor.executeAction (type dispatch, PerformanceMonitor timed)
```

## Risk Matrix

| Action        | Risk       | Confirm | Permissions     |
|---------------|------------|---------|-----------------|
| open_app      | LOW        | No      | —               |
| tap/swipe/back| LOW        | No      | accessibility   |
| type          | MEDIUM     | No      | accessibility   |
| wifi/bluetooth| MEDIUM     | No      | —               |
| send_sms      | HIGH       | Yes     | sms             |
| make_call     | HIGH       | Yes     | phone           |
| credential_*  | FORBIDDEN  | —       | —               |
| security_*    | FORBIDDEN  | —       | —               |
| financial_*   | FORBIDDEN  | —       | —               |

## Performance Monitoring

```
PerformanceMonitor (thread-safe, CopyOnWriteArrayList)
    → Bounded ring buffer (1000 samples max)
    → Timers: onnx_mel, onnx_embedding, onnx_classifier
              stt_listen, tts_speak, action_plan_total, action_{type}
    → Stats: p50/p95/p99/avg/min/max per timer
    → Memory snapshots: heap, native
```

## Testing

- Android: JUnit (auth, action policy, privacy, performance monitor)
- Backend: Jest — 93 tests across 8 suites
  - actionPolicy (schema, risk, forbidden)
  - wsSchemas (message validation)
  - rateLimit (per-endpoint, config)
  - actionRegistry (forbidden types, risk assignment)
  - enrollment (pairing code, token flow)
  - authSecurity (token lifecycle, uniqueness, hashing, WS tickets)
  - actionSecurity (token isolation, forbidden block, high-risk confirm/deny, memory/skill isolation)
  - goldenPath (12-step E2E, WS auth integration, rate limit config, security invariants)
- Run: `cd android && ./gradlew compileDebugKotlin` (Android)
- Run: `cd backend && npm test` (backend)
- CI: lint + compileDebugKotlin + assembleRelease (master push)
