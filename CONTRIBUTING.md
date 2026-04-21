# Contributing

## Workflow

1. Keep changes scoped to either Android, backend, or shared documentation when possible.
2. Update docs when architecture, setup, or API behavior changes.
3. Run the smallest relevant validation command before opening a pull request.

## Validation

- Android: `cd android && ./gradlew test`
- Backend: `cd backend && python -m compileall .`
- Integration smoke test when relevant: `python scripts/api_smoke.py --profile local`

## Secrets

- Do not commit `.env` files or filled `local.properties`.
- Keep API URLs and signing data in local machine config or CI secrets.
