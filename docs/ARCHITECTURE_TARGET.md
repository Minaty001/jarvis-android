# Target Architecture

## Android

```
                    JARVIS
                      │
                AssistantRuntime
                      │
       ┌──────────────┼──────────────┐
       │              │              │
 VoiceRuntime    CommandRuntime   DataRuntime
       │              │              │
       │          ActionPlan       Memory
       │              │              │
       │        PolicyValidator     Skills
       │              │
       │       ConfirmationManager
       │              │
       └──────────→ ActionExecutor
                         │
              ┌──────────┼──────────┐
              ↓          ↓          ↓
        Accessibility   Android   App APIs
```

## Backend

```
Android
   ↓
Auth (persistent device_sessions)
   ↓
Authorized Device
   ↓
Command Schema
   ↓
LLM Planner
   ↓
Strict ActionPlan
   ↓
Policy
   ↓
Response
```

## Key Principles

1. **Single ownership**: One runtime owns voice, one owns commands, one owns data
2. **State-driven**: All components react to state changes, not method calls
3. **Auth-first**: Nothing connects or executes without valid auth state
4. **Strict boundaries**: LLM output validated at every layer
5. **Observable**: Every action has a requestId, latency, and result
