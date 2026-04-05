# AGENTS.md

Repository-level guidance for Codex working in this project. Keep this file practical and execution-oriented. Use `Docs/` for full product and design detail.

## What This Repo Is

NyxGuard is an Android + FastAPI project for night-travel safety scenarios.

- Android client lives in `app/`
- FastAPI backend lives in `backend/`
- Product, feature, and database docs live in `Docs/`
- Helper scripts live in `scripts/`

Do not describe this repository as Android-only. The current architecture is Android app + independently deployed FastAPI backend.

## Repo Layout

### Android

- Main package: `com.scf.nyxguard`
- UI is View-based with ViewBinding and Material components
- Main Android feature areas currently include:
  - `ui/` for screens and shared UI
  - `walk/` for walking mode
  - `ride/` for ride mode
  - `chat/` for AI companion
  - `fakecall/` for fake-call flow
  - `profile/` for user/profile/guardian flows
  - `network/`, `data/`, `service/`, `util/`, `common/` for shared plumbing

### Backend

- FastAPI app factory and local launcher: `backend/main.py`
- ASGI entrypoint for deployment: `backend/server.py`
- Compatibility launcher: `backend/app.py`
- API routers live in `backend/routers/`
- SQLAlchemy setup lives in `backend/database.py`, `backend/models.py`, `backend/schemas.py`
- Runtime config lives in `backend/config.py`

### Documentation

Use the docs below when behavior, terminology, or business rules matter:

- `Docs/需求规格说明书.md`
- `Docs/功能设计说明书.md`
- `Docs/数据库设计说明书.md`

Keep `AGENTS.md` short. Do not duplicate long product specs here unless they are needed repeatedly during implementation.

## Working Style

- Start by reading the relevant code and docs before changing architecture or behavior.
- Prefer small, scoped changes over broad rewrites.
- Preserve existing patterns unless the task explicitly calls for refactoring.
- When a requirement is safety-sensitive or user-facing, verify it against `Docs/` instead of guessing.
- If behavior changes, update the relevant Markdown docs in the same pass when practical.

## Build, Run, and Test

Run commands from the repository root unless noted otherwise.

### Android builds

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

### Android tests

```bash
./gradlew test
./gradlew connectedAndroidTest
```

If you need a narrower test target, prefer the smallest relevant Gradle task for the affected flavor or test class.

### Backend setup and run

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.local.example .env
python app.py
```

Alternative local launch:

```bash
cd backend
uvicorn server:app --host 0.0.0.0 --port 5001 --reload
```

### API smoke test

Use before demos or Android/backend integration work:

```bash
python3 scripts/api_smoke.py --profile local
```

## Configuration Rules

### Android build config

Do not hardcode API base URLs in Kotlin code.

Provide the Android API URL through Gradle properties:

- `nyxGuardApiBaseUrl`

Backward-compatible fallback property names:

- `nyxGuardProdApiBaseUrl`
- `nyxGuardStagingApiBaseUrl`
- `nyxGuardLocalApiBaseUrl`

Preferred locations:

1. `~/.gradle/gradle.properties`
2. project `local.properties`

Machine-specific paths for Android Studio, JBR, or `aapt2` belong in local machine config, not in the repository.

### Backend env config

Backend runtime configuration comes from `backend/.env`.

Important variables:

- `APP_ENV`
- `LOG_LEVEL`
- `JWT_SECRET_KEY`
- `CORS_ALLOW_ORIGINS`
- `DATABASE_URL`
- `DATABASE_URL_NON_POOLING`
- `OPENAI_API_KEY`

Local development uses SQLite fallback. Preview and production use Neon Postgres.

Keep the backend env contract stable:

- `DATABASE_URL` for runtime traffic
- `DATABASE_URL_NON_POOLING` for direct/non-pooled connections

## Engineering Conventions

### Android

- Keep using Kotlin + ViewBinding + Material Design.
- Follow existing package structure instead of creating one-off utility locations.
- Reuse `BuildConfig` environment fields instead of duplicating environment logic.
- Prefer targeted UI/resource changes; avoid unnecessary layout churn.

### Backend

- Keep the FastAPI router structure intact unless a broader refactor is requested.
- Use SQLAlchemy models/schemas consistently instead of ad hoc SQL unless there is a clear reason.
- Preserve JWT-based auth flow and current env-variable naming.
- Keep `backend/server.py` as a thin ASGI entrypoint for deployment compatibility.

### Data and product rules

Before changing any of the following, verify the requirement in `Docs/`:

- walking deviation threshold
- ride deviation threshold
- arrival radius
- guardian-count limits
- SOS persistence rules
- location upload cadence
- AI fallback behavior

These are product rules, not implementation details.

## Do Not Rules

- Do not commit secrets, real API keys, or filled `.env` files.
- Do not commit machine-specific `local.properties` values unless the task explicitly requires it.
- Do not silently rename APIs, tables, or env vars that Android and backend both depend on.
- Do not replace the documented Android + FastAPI architecture with stale wording from older docs or slides.
- Do not edit generated or binary materials (`Docs/PPT/*.pptx`, PDFs) unless the task explicitly asks for that format.

## Done Means

A task is not complete until all of the following that apply are handled:

- the code change is implemented in the right layer
- affected docs are updated if behavior or architecture changed
- the smallest relevant validation has been run
- any assumptions or unverified areas are called out clearly

Validation guidance:

- Android UI/code change: at minimum run the relevant Gradle build or test task
- Backend API change: at minimum run the backend locally or run the relevant smoke/test flow
- Cross-stack integration change: prefer both an Android build and `scripts/api_smoke.py`

## Reference Commands

```bash
# clean Android build
./gradlew clean assembleDebug

# run all unit tests
./gradlew test

# run instrumented tests
./gradlew connectedAndroidTest

# backend local server
cd backend && python app.py

# backend smoke test
python3 scripts/api_smoke.py --profile local
```

## Documentation Sync Notes

If you update Markdown docs that affect project positioning or architecture, remember that these binary materials do not auto-sync and may need manual follow-up:

- `Docs/PPT/*.pptx`
- `Docs/项目介绍.pdf`
- `Docs/课程大纲.pdf`
