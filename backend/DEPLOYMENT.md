# Backend Deployment

## Scope
- Local development keeps SQLite.
- Preview and production use Neon Postgres.
- First launch assumes standalone Python hosting for the backend.
- Vercel deployment is supported when `backend/` is deployed as its own project root.

## Runtime
- Framework: FastAPI
- ASGI entrypoint: `backend/server.py`
- Import path: `server:app`
- Vercel entrypoint: `backend/index.py`
- Local launcher: `python app.py`
- Direct ASGI launch: `uvicorn server:app --host 0.0.0.0 --port 5001`
- Production launch: `gunicorn -k uvicorn.workers.UvicornWorker --bind 0.0.0.0:${PORT:-5001} server:app`

## Environment Templates
- `backend/.env.example`
- `backend/.env.local.example`
- `backend/.env.preview.example`
- `backend/.env.production.example`

For local runs, copy the right template to `backend/.env`.

Android API base URLs do not belong in these backend env files. Keep Android flavor URLs in Gradle properties instead:

- `nyxGuardLocalApiBaseUrl` (optional for `localDebug`; defaults to `http://10.0.2.2:5001/` on the Android Emulator)
- `nyxGuardStagingApiBaseUrl`
- `nyxGuardProdApiBaseUrl`

## Environment Variables

### Required
- `APP_ENV`
- `LOG_LEVEL`
- `JWT_SECRET_KEY`
- `CORS_ALLOW_ORIGINS`
- `DATABASE_URL`

### Optional
- `DATABASE_URL_NON_POOLING`
- `OPENAI_API_KEY`

Outside `development`, the app now fails fast if:
- `DATABASE_URL` is missing or still points to SQLite
- `JWT_SECRET_KEY` is missing or still uses the repository default

## Database
- Local development: `DATABASE_URL=sqlite:///./nyxguard.db`
- Preview / production: Neon Postgres
- Runtime traffic should use pooled `DATABASE_URL`
- Future migrations or admin scripts should use `DATABASE_URL_NON_POOLING`
- Automatic `create_all` / schema compatibility fixes only run in local `development` with SQLite
- Preview / production schema bootstrap must run explicitly before first traffic

## Neon Notes
- Use the Neon pooled connection string for `DATABASE_URL`
- Use the Neon direct or non-pooled connection string for `DATABASE_URL_NON_POOLING`
- Keep `sslmode=require`
- Keep `channel_binding=require` when Neon includes it
- If Neon returns a `postgres://` or `postgresql://` URL, `backend/config.py` normalizes it to SQLAlchemy's `postgresql+psycopg://`

## Local Development
```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.local.example .env
python app.py
```

Health checks:
- `GET /`

## Preview / Production Deployment
Any generic Python host is acceptable for first launch, including Railway, Render, Fly.io, Cloud Run, or your own container runtime.

Install:
```bash
cd backend
pip install -r requirements.txt
```

Start:
```bash
gunicorn -k uvicorn.workers.UvicornWorker --bind 0.0.0.0:${PORT:-5001} server:app
```

Bootstrap schema before first traffic:
```bash
cd backend
python bootstrap_schema.py
```

## Suggested Environment Mapping

### Local
- `APP_ENV=development`
- `DATABASE_URL=sqlite:///./nyxguard.db`
- `DATABASE_URL_NON_POOLING=`
- `CORS_ALLOW_ORIGINS=*`

### Preview
- `APP_ENV=preview`
- `DATABASE_URL=<Neon pooled preview URL>`
- `DATABASE_URL_NON_POOLING=<Neon direct preview URL>`
- `CORS_ALLOW_ORIGINS=<preview frontend origin>`

### Production
- `APP_ENV=production`
- `DATABASE_URL=<Neon pooled production URL>`
- `DATABASE_URL_NON_POOLING=<Neon direct production URL>`
- `CORS_ALLOW_ORIGINS=<production frontend origin list>`

## Android Flavor Mapping

- `localDebug` -> local FastAPI + local SQLite
- `stagingDebug` -> preview FastAPI + Neon preview
- `prodDebug` -> production API base URL with debug tooling
- `prodRelease` -> production FastAPI + Neon production

Recommended Android URL sources:

- first choice: `~/.gradle/gradle.properties`
- fallback: project `local.properties`

## Future Vercel Migration
- Deploy `backend/` as the Vercel Project Root Directory, not the repository root.
- `backend/index.py` exposes the Vercel entrypoint and `backend/vercel.json` sets function limits.
- Keep `DATABASE_URL` as the runtime URL and `DATABASE_URL_NON_POOLING` as the direct URL.
- If Vercel or a Vercel Marketplace integration injects provider-specific Postgres variables later, map them into these two names instead of changing application code.
- Do not mount this backend under a Vercel Services `routePrefix` of `/api`, because the application already owns `/api/...` routes.
