# AGENTS.md

Repository-level guidance for Codex working in this project.

## What This Repo Is

NyxGuard is an Android + FastAPI project for night-travel safety scenarios.

- Android client lives in `android/`
- FastAPI backend lives in `backend/`
- Product and database docs live in `docs/`
- Helper scripts live in `scripts/`

Do not describe this repository as Android-only.

## Repo Layout

- `android/app/` Android application module
- `backend/` FastAPI runtime and deployment entrypoints
- `docs/` architecture, feature, database, and setup documentation
- `scripts/` helper scripts and smoke checks

## Working Rules

- Start by reading the relevant code and docs before changing behavior.
- Prefer scoped changes over broad rewrites.
- Keep Android path references under `android/`, not the repository root.
- Keep backend commands rooted in `backend/`.
- Update docs when architecture, setup, or API behavior changes.

## Validation

- Android: `cd android && ./gradlew test`
- Android build: `cd android && ./gradlew assembleDebug`
- Backend import check: `cd backend && python -m compileall .`
- Cross-stack smoke test when relevant: `python scripts/api_smoke.py --profile local`

## Configuration Rules

- Android local config belongs in `android/local.properties`, based on `android/local.properties.example`.
- Do not hardcode backend URLs in tracked Kotlin files.
- Backend runtime config belongs in `backend/.env`.
- Do not commit secrets or filled local machine config.

## Product Rules

Before changing thresholds or trip-protection behavior, verify requirements in the docs under `docs/`, especially:

- walking deviation thresholds
- ride deviation thresholds
- arrival radius
- guardian limits
- SOS persistence behavior

## Binary and Generated Files

Do not edit generated or binary materials under `docs/` unless the task explicitly requires that format.
