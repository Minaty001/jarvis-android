# JARVIS AI — Android Voice Assistant

A lightweight, AI-powered Android voice assistant with on-device ONNX wake word detection, full device automation, and cloud LLM backend.

## Architecture

```
Android App (Kotlin/Compose)
    ├── Voice: ONNX 3-Model Wake Word → SpeechRecognizer STT → Backend → TTS
    ├── Automation: AccessibilityService + AppController + ActionValidator
    ├── Memory: Room DB (local) + Supabase (cloud)
    ├── Skills: ActionRecorder + SkillExecutor
    ├── Connectivity: Bluetooth, WiFi, Battery
    ├── Communication: SMS, Contacts, Calendar
    └── Sharing: Text, Images, Files

Backend (Node.js/Express)
    ├── LLM: Groq → OpenRouter → NVIDIA NIM (fallback chain)
    ├── Memory: Supabase pgvector (cosine similarity) / keyword fallback
    ├── Skills: 3-tier matching (exact → semantic → LLM)
    ├── Auth: Opaque bearer token + device registration
    ├── Rate Limiting: 30 req/min per IP, 20 cmd/min per WS connection
    └── WebSocket: Real-time command streaming (Zod-validated messages)
```

## Features

### Voice
- **Wake word detection**: ONNX 3-model pipeline (melspectrogram + embedding + classifier) — fully offline, zero cloud dependency
- **STT**: Android SpeechRecognizer (cloud-dependent system service)
- **TTS**: Android TextToSpeech engine
- **VoiceInputMode**: OFF → WAKE_WORD → COMMAND state machine

### Automation
- Open/close any app (70+ aliases)
- Tap, swipe, type via AccessibilityService
- Read screen content (password-masked, returned to LLM)
- YouTube/Chrome/WhatsApp sequences (event-based waits, no Thread.sleep)
- Action validation with risk levels (AUTOMATIC/LOW/MEDIUM/HIGH/FORBIDDEN)
- Confirmation dialog for high-risk actions (SMS, etc.)
- Strict backend LLM action schema (18 allowed types, per-action param validation)

### Memory & Learning
- pgvector cosine similarity search (when NVIDIA NIM available)
- Keyword fallback when embeddings unavailable
- Local Room DB cache + background sync
- 3-tier skill matching
- Record & execute learned skills

### Security
- Opaque bearer token (crypto.randomBytes) with server-side storage
- EncryptedSharedPreferences for token storage
- ActionValidator blocks unknown actions + validates per-action params
- Confirmation gate for high-risk actions (suspendCancellableCoroutine + Compose dialog)
- PrivacyFilter blocks sensitive screen content from LLM
- Feature-gated permissions (on-demand, not bulk)
- Backend auth middleware on all protected REST routes
- WebSocket requires valid token + device ID on connection
- WS messages Zod-validated (discriminated union: command | ping)
- Per-connection WS rate limiting (20 cmd/min)
- Cleartext traffic disabled (network_security_config.xml for localhost only)

### Connectivity
- Bluetooth toggle, discovery, pairing
- WiFi toggle, connection info
- Battery status, health, temperature

### Communication
- Send SMS messages
- Read contacts, make calls
- Read calendar events, search calendar
- Send WhatsApp messages

### Sharing
- Share text to any app
- Share images and files
- FileProvider for secure sharing

## Quick Start

### Backend

```bash
cd backend
cp .env.example .env
# Edit .env with your API keys (GROQ_API_KEY, OPENROUTER_API_KEY, etc.)
npm install
npm start
```

Server runs on `http://localhost:10000`

### Android

1. Open `android/` in Android Studio
2. Sync Gradle
3. Run on device or emulator

**Note**: The ONNX wake word models (`melspectrogram.onnx`, `embedding_model.onnx`, `hey_jarvis.onnx`) are included in `android/app/src/main/assets/wakeword/` and are loaded asynchronously at detector construction.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /health | Server status + LLM providers |
| POST | /api/v1/auth/token | Device registration (returns opaque token) |
| POST | /api/v1/auth/refresh | Token refresh |
| POST | /command | Send voice command (auth required) |
| WS | /ws?device=ID&token=TOKEN | WebSocket command stream (auth required) |
| GET | /memory/search | Search memories (auth required) |
| GET | /memory/recent | Recent memories (auth required) |
| POST | /memory/store | Store a memory (auth required) |
| DELETE | /memory/:id | Delete a memory (auth required, ownership enforced) |
| GET | /memory/stats | Memory + skill counts (auth required) |
| POST | /skill/learn | Learn a new skill (auth required) |
| GET | /skill/match | Match command to skill (auth required) |
| GET | /skill/list | List all skills (auth required) |
| DELETE | /skill/:id | Delete a skill (auth required, ownership enforced) |

## Auth Flow

```
App start
    ↓
load installation UUID (Config.getDeviceId)
    ↓
POST /api/v1/auth/token { device_id: uuid }
    ↓
save opaque access_token + refresh_token + expiry
    ↓
WebSocket connect (?device=uuid&token=access_token)
    ↓
REST calls with Authorization: Bearer access_token
    ↓
on 401 → POST /api/v1/auth/refresh → update tokens
```

## Deployment

### Render.com (Backend)
- Free tier (512MB RAM)
- WebSocket support
- Auto-sleep after 15min inactivity

### Supabase (Database)
Run `supabase/schema.sql` in Supabase SQL Editor to create:
- `memories` table with pgvector
- `skills` table with pgvector
- `match_memories()`, `match_skills()`, `increment_skill_usage()` functions

## Tech Stack

- **Android**: Kotlin, Jetpack Compose, Room, WorkManager, ONNX Runtime Mobile
- **Backend**: Node.js, Express, WebSocket, Zod, crypto
- **Database**: Supabase (PostgreSQL + pgvector)
- **LLM**: Groq, OpenRouter, NVIDIA NIM
- **Wake Word**: ONNX 3-model pipeline (offline, on-device)
- **Auth**: Opaque bearer tokens with encrypted local storage

## Project Structure

```
project1/
├── android/                    # Android app
│   └── app/src/main/
│       ├── assets/wakeword/    # ONNX wake word models
│       ├── java/com/jarvis/
│       │   ├── automation/     # AccessibilityService, ActionValidator, SkillExecutor
│       │   ├── audio/          # ClapDetector
│       │   ├── backend/        # WebSocketClient, ApiClient, AuthTokenManager, ConnectionManager
│       │   ├── calendar/       # CalendarManager
│       │   ├── connectivity/   # BluetoothController, WifiController, BatteryMonitor
│       │   ├── contacts/       # ContactManager
│       │   ├── files/          # MediaStoreManager
│       │   ├── media/          # MediaNotificationListener
│       │   ├── memory/         # Room DB, SyncWorker
│       │   ├── messaging/      # SmsController
│       │   ├── navigation/     # NavigationController
│       │   ├── permissions/    # PermissionManager (feature-gated)
│       │   ├── phone/          # PhoneController
│       │   ├── reminders/      # ReminderScheduler
│       │   ├── sharing/        # ShareManager
│       │   ├── stt/            # NativeSttManager
│       │   ├── tts/            # TtsManager
│       │   ├── ui/             # Compose screens + theme
│       │   └── wakeword/       # ONNX wake word pipeline (7 files)
│       └── res/                # XML resources
├── backend/                    # Node.js backend
│   └── src/
│       ├── core/               # LLM, CommandRouter, MemoryManager, SessionManager
│       ├── middleware/         # Sanitization, Auth
│       └── routes/             # REST endpoints
└── supabase/                   # Database schema
```

## Wake Word Details

The wake word system uses a 3-stage ONNX neural pipeline:

1. **Mel Spectrogram** (`melspectrogram.onnx`): Converts raw 16kHz PCM audio to mel spectrogram features
2. **Embedding Model** (`embedding_model.onnx`): Generates 96-dimensional embeddings from spectrogram windows
3. **Classifier** (`hey_jarvis.onnx`): Custom-trained conv-attention classifier for "Hey Jarvis" detection

Features:
- **Temporal Gate**: Requires 5/7 consecutive windows above confidence threshold
- **Adaptive Noise Gate**: Auto-calibrates noise floor, skips inference for quiet audio
- **Cooldown**: 4-second debounce after confirmed detection
- **Fully offline**: Zero cloud dependency for wake word detection
- **Async loading**: Models load on Dispatchers.IO, not blocking main thread
- **Configurable sensitivity**: Low/Medium/High (0.3–0.7 threshold range)
