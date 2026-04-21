# Architecture

NyxGuard is a client-server system split between an Android app and a FastAPI backend.

## Main parts

- `android/` mobile client built with Kotlin, ViewBinding, and Material components
- `backend/` FastAPI API, SQLAlchemy models, auth, and deployment entrypoints
- `docs/` product and database reference material
- `scripts/` helper scripts and smoke tests

## Runtime flow

1. The Android client reads backend URLs from local Gradle properties.
2. The backend serves authenticated APIs and persistence.
3. Product rules and thresholds are documented in the project docs under `docs/`.
