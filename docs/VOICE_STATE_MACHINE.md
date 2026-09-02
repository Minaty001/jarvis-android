# Voice State Machine

## States

```
OFF
  ↓ (user enables wake)
INITIALIZING
  ↓ (models loaded)
READY
  ↓ (start monitoring)
WAKE_LISTENING
  ↓ (wake word detected)
COMMAND_LISTENING
  ↓ (STT result)
PROCESSING
  ↓ (action executed)
SPEAKING
  ↓ (TTS complete)
WAKE_LISTENING

Any state → ERROR (on failure)
Any state → OFF (user disables)
```

## Transitions

| From | To | Trigger |
|------|-----|---------|
| OFF | INITIALIZING | User enables wake word |
| INITIALIZING | READY | ONNX models loaded |
| INITIALIZING | ERROR | Model load failed |
| READY | WAKE_LISTENING | startMonitoring() |
| WAKE_LISTENING | COMMAND_LISTENING | Wake word detected |
| COMMAND_LISTENING | PROCESSING | STT returns text |
| COMMAND_LISTENING | WAKE_LISTENING | STT error/timeout |
| PROCESSING | SPEAKING | TTS starts |
| PROCESSING | WAKE_LISTENING | No TTS needed |
| SPEAKING | WAKE_LISTENING | TTS complete |
| Any | ERROR | Failure |
| Any | OFF | User disables |

## Rules

- ONE audio owner at a time (WAKE or COMMAND, never both)
- ClapDetector = separate mode (not concurrent with wake)
- ONNX models load on IO thread, not constructor
- STT pauses wake engine, resume after STT completes
- Confirmation state blocks voice (user must respond)
