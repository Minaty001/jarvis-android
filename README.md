# JARVIS AI — Android Voice Assistant

A lightweight, AGI-powered Android voice assistant with full device automation, running on free-tier infrastructure.

## Architecture

```
Android App (Kotlin/Compose)
    ├── Voice: Wake Word → STVosk → Backend → TTS
    ├── Automation: AccessibilityService + AppController
    ├── Memory: Room DB (local) + Supabase (cloud)
    ├── Skills: ActionRecorder + SkillExecutor
    ├── Connectivity: Bluetooth, WiFi, Battery
    ├── Communication: SMS, Contacts, Calendar
    └── Sharing: Text, Images, Files

Backend (Node.js/Express)
    ├── LLM: Groq → OpenRouter → NVIDIA NIM (fallback chain)
    ├── Memory: Supabase pgvector (cosine similarity)
    ├── Skills: 3-tier matching (exact → semantic → LLM)
    ├── Rate Limiting: 30 req/min per IP
    └── WebSocket: Real-time command streaming
```

## Features

### Voice
- Wake word detection (openwakeword)
- Double clap activation
- Offline STT (Vosk)
- Text-to-speech (Android TTS)

### Automation
- Open/close any app
- Tap, swipe, type via AccessibilityService
- Read screen content
- YouTube/Chrome/WhatsApp sequences

### Memory & Learning
- pgvector cosine similarity search
- Local Room DB cache + background sync
- 3-tier skill matching
- Record & execute learned skills

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
# Edit .env with your API keys
npm install
npm start
```

Server runs on `http://localhost:10000`

### Android

1. Open `android/` in Android Studio
2. Sync Gradle
3. Run on device or emulator

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /health | Server status + LLM providers |
| POST | /command | Send voice command |
| WS | /ws | WebSocket command stream |
| GET | /memory/search | Search memories (pgvector) |
| GET | /memory/recent | Recent memories |
| POST | /memory/store | Store a memory |
| DELETE | /memory/:id | Delete a memory |
| GET | /memory/stats | Memory + skill counts |
| POST | /skill/learn | Learn a new skill |
| GET | /skill/match | Match command to skill |
| GET | /skill/list | List all skills |
| DELETE | /skill/:id | Delete a skill |

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

- **Android**: Kotlin, Jetpack Compose, Room, WorkManager
- **Backend**: Node.js, Express, WebSocket, Zod
- **Database**: Supabase (PostgreSQL + pgvector)
- **LLM**: Groq, OpenRouter, NVIDIA NIM
- **Voice**: openwakeword, Vosk, Android TTS

## Project Structure

```
project1/
├── android/                    # Android app (45 Kotlin files)
│   └── app/src/main/java/com/jarvis/
│       ├── automation/         # AccessibilityService, AppController, SkillExecutor
│       ├── audio/              # ClapDetector
│       ├── backend/            # WebSocketClient, ApiClient
│       ├── calendar/           # CalendarManager
│       ├── connectivity/       # BluetoothController, WifiController, BatteryMonitor
│       ├── contacts/           # ContactManager
│       ├── files/              # MediaStoreManager
│       ├── media/              # MediaNotificationListener
│       ├── memory/             # Room DB, SyncWorker
│       ├── messaging/          # SmsController
│       ├── navigation/         # NavigationController
│       ├── phone/              # PhoneController
│       ├── reminders/          # ReminderScheduler
│       ├── sharing/            # ShareManager
│       ├── stt/                # VoskManager
│       ├── tts/                # TtsManager
│       ├── ui/                 # Compose screens + theme
│       └── wakeword/           # WakeWordManager
├── backend/                    # Node.js backend (11 JS files)
│   └── src/
│       ├── core/               # LLM, CommandRouter, MemoryManager, SessionManager
│       ├── middleware/         # Sanitization
│       └── routes/             # REST endpoints (12 total)
└── supabase/                   # Database schema
```
