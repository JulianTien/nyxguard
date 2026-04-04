# NyxGuard

NyxGuard is a safety companion app designed for women traveling at night. It focuses on two high-frequency scenarios, walking alone and taking a ride, and combines real-time trip protection, proactive AI care, and emergency assistance into one product.

This repository contains both the Android client and the FastAPI backend for the project.

## Highlights

- Walking mode with trip setup, route planning, live tracking, timeout checks, and route deviation alerts
- Ride mode with vehicle information input, trip tracking, deviation alerts, and one-tap SOS
- AI companion chat with a warm guardian persona and local fallback responses when the network is unavailable
- SOS flow with countdown confirmation, local audio placeholder capture, emergency event recording, and guardian notification hooks
- Guardian management for trusted contacts
- Fake call flow for uncomfortable situations

## Architecture

NyxGuard follows a client-server architecture:

- Android app: Kotlin, View-based UI with ViewBinding, Material Design, Retrofit/OkHttp/Gson, AMap SDK
- Backend API: FastAPI, SQLAlchemy, JWT authentication
- Database: Neon Postgres in production, SQLite fallback for local development and demos
- Deployment strategy: FastAPI as an independently deployed backend; `backend/` can also be deployed directly to Vercel as its own project root

## Repository Structure

```text
NyxGuard/
  app/        Android client
  backend/    FastAPI backend
  Docs/       Product, design, database, and course documents
  scripts/    Helper scripts
```

## Core Features

### P0

- User management: registration, login, profile, and guardian management
- Walking mode: setup, guardian selection, navigation context, live tracking, timeout detection, deviation detection, and arrival confirmation
- Ride mode: vehicle information, guardian selection, live tracking, deviation alerts, and SOS
- AI care chat: user-initiated chat and proactive care hooks
- Emergency SOS: countdown confirmation and event persistence

### P1

- Fake call simulation
- Guardian notification pipeline

### P2

- OCR plate recognition
- Guardian-side mini app

## Tech Stack

### Android

- Kotlin
- ViewBinding
- Material Design
- Retrofit
- OkHttp
- Gson
- AMap Android SDK

### Backend

- FastAPI
- SQLAlchemy
- PyJWT
- bcrypt
- psycopg
- pydantic-settings

## Getting Started

### Prerequisites

- Android Studio with Android SDK
- JDK 11
- Python 3.9+ recommended
- An AMap key for Android map and location capabilities

### Android App

NyxGuard now uses an `env` flavor dimension:

- `localDebug`: local FastAPI + SQLite, debug mock fallback enabled
- `stagingDebug`: preview FastAPI + Neon preview, debug mock fallback enabled
- `prodDebug`: production API contract with debug tooling, mock fallback disabled
- `prodRelease`: production build, mock fallback disabled

Provide Android API URLs through Gradle properties in either `~/.gradle/gradle.properties` or project `local.properties`.

Supported property names:

- `nyxGuardLocalApiBaseUrl` (optional for `localDebug`; defaults to `http://10.0.2.2:5001/` for the Android Emulator)
- `nyxGuardStagingApiBaseUrl`
- `nyxGuardProdApiBaseUrl`

`stagingDebug`, `prodDebug`, and `prodRelease` still require explicit Gradle properties. If you run `localDebug` on a physical device or a third-party emulator, override `nyxGuardLocalApiBaseUrl` with a host that can reach your machine.

Machine-specific paths for the Android Studio JBR and `aapt2` should stay in `~/.gradle/gradle.properties`, not in the repository.

Build the local debug APK from the repository root:

```bash
./gradlew assembleLocalDebug
```

Build the preview debug APK:

```bash
./gradlew assembleStagingDebug
```

Build the production release APK:

```bash
./gradlew assembleProdRelease
```

Run unit tests:

```bash
./gradlew test
```

Run a single unit test class:

```bash
./gradlew testDebugUnitTest --tests "com.scf.nyxguard.ExampleUnitTest"
```

Run instrumented tests on a connected device or emulator:

```bash
./gradlew connectedAndroidTest
```

### Backend API

Create and activate a virtual environment, then install dependencies:

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Create a local environment file:

```bash
cp .env.example .env
```

Start the API server:

```bash
uvicorn main:app --host 0.0.0.0 --port 5001 --reload
```

The API will be available at:

- `http://127.0.0.1:5001`
- `http://127.0.0.1:5001/docs` for Swagger UI in non-production mode

## API Overview

Main endpoints include:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/user/profile`
- `PUT /api/user/profile`
- `GET /api/guardians`
- `POST /api/guardians`
- `DELETE /api/guardians/{id}`
- `POST /api/trips`
- `GET /api/trips/{id}`
- `POST /api/trips/{id}/locations`
- `PUT /api/trips/{id}/finish`
- `POST /api/trips/{id}/sos`
- `GET /api/notifications/events`
- `POST /api/chat`
- `POST /api/chat/proactive`

## Configuration

Android client configuration and backend runtime configuration are intentionally separate.

### Android Build Config

Android URLs are compiled into:

- `BuildConfig.NYXGUARD_ENV`
- `BuildConfig.NYXGUARD_API_BASE_URL`
- `BuildConfig.NYXGUARD_ENABLE_DEBUG_MOCK_FALLBACK`

Flavor mapping:

- `local` -> local FastAPI + SQLite
- `staging` -> preview FastAPI + Neon preview
- `prod` -> production FastAPI + Neon production

### Backend Env

The backend reads runtime configuration from `backend/.env`.

Important variables:

- `APP_ENV`
- `LOG_LEVEL`
- `JWT_SECRET_KEY`
- `CORS_ALLOW_ORIGINS`
- `DATABASE_URL`
- `DATABASE_URL_NON_POOLING`
- `OPENAI_API_KEY`

By default, the backend falls back to a local SQLite database only in `development`. Non-development environments now require an explicit Postgres `DATABASE_URL` and a non-default `JWT_SECRET_KEY`.

### API Smoke Test

Run the bundled smoke test before demos or Android/backend integration work:

```bash
python3 scripts/api_smoke.py --profile local
```

Target preview or production explicitly when needed:

```bash
python3 scripts/api_smoke.py --profile staging --base-url https://preview-api.example.com
python3 scripts/api_smoke.py --profile prod --base-url https://api.example.com
```

See `scripts/API_SMOKE.md` for the full workflow.

The smoke flow now verifies profile updates, guardian-linked trip creation, and notification event generation.

## Product Rules

- Walking deviation threshold: 200 meters
- Ride deviation threshold: 500 meters
- Arrival detection radius: 100 meters
- Walking tracking frequency: every 10 seconds, uploaded every 30 seconds in batches
- Ride tracking frequency: every 5 seconds, uploaded every 15 seconds in batches
- SOS tracking frequency: every 3 seconds after activation
- Maximum guardians per user: 5

## Documentation

Detailed project documentation is available in the `Docs/` directory:

- `Docs/需求规格说明书.md`
- `Docs/功能设计说明书.md`
- `Docs/数据库设计说明书.md`
- `Docs/项目介绍.pdf`
- `Docs/课程大纲.pdf`

Binary materials such as PDF and PPT files should be updated manually when documentation wording changes.

## Status

The repository already includes a working Android client skeleton and FastAPI backend integration. Some advanced items, such as OCR plate recognition and complete push notification integration, are still planned or partially implemented.

## License

No license file has been added yet. If you plan to open source this project publicly, adding a license such as MIT is recommended.
