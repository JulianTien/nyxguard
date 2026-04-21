# NyxGuard

NyxGuard is a night-travel safety platform with an Android client and a FastAPI backend in one repository.

## Repository Layout

- `android/` Android application project and Gradle wrapper
- `backend/` FastAPI service, deployment entrypoints, and Python config
- `docs/` product, architecture, database, and setup documentation
- `scripts/` smoke tests and helper utilities

## Quick Start

### Android

```bash
cd android
./gradlew test
./gradlew assembleDebug
```

Use `android/local.properties.example` as the template for local machine config.

### Backend

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python app.py
```

Alternative launch:

```bash
cd backend
uvicorn server:app --host 0.0.0.0 --port 5001 --reload
```

## Documentation

- [Architecture](docs/architecture.md)
- [API Summary](docs/api.md)
- [Android Setup](docs/android-setup.md)
- [Backend Setup](docs/backend-setup.md)

Detailed product docs remain under `docs/`.

## Release and CI

- Android CI runs only on `android/**` changes.
- Backend CI runs only on `backend/**` and backend-adjacent changes.
- Release workflow packages build artifacts for tagged versions.

## Contributing

Review [CONTRIBUTING.md](CONTRIBUTING.md) and keep Android and backend changes aligned with the docs in `docs/`.
