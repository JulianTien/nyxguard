# API Smoke Test

`scripts/api_smoke.py` is the standard quick validation entrypoint for NyxGuard FastAPI.

It covers:

- health
- register
- login
- get profile
- update profile
- add guardian
- list guardians
- create trip with `guardian_ids`
- get trip with guardian linkage
- upload locations
- chat
- proactive chat
- trigger SOS
- finish trip
- list notification events
- negative checks for `401`, `404`, and `400`

## Usage

Local development:

```bash
python3 scripts/api_smoke.py --profile local
```

Preview validation:

```bash
python3 scripts/api_smoke.py --profile staging --base-url https://preview-api.example.com
```

Production-style validation:

```bash
python3 scripts/api_smoke.py --profile prod --base-url https://api.example.com
```

Override password or timeout when needed:

```bash
python3 scripts/api_smoke.py --profile local --password abc123 --timeout 15
```

## Base URL Resolution

The script resolves the API URL in this order:

1. `--base-url`
2. `NYXGUARD_API_BASE_URL`
3. `NYXGUARD_LOCAL_API_BASE_URL`, `NYXGUARD_STAGING_API_BASE_URL`, or `NYXGUARD_PROD_API_BASE_URL`
4. `http://127.0.0.1:5001` when `--profile local`

For `staging` and `prod`, missing URLs fail fast.

## Notes

- Each run creates a unique test user to avoid account collisions.
- The smoke trip validates guardian binding, profile updates, and notification event generation.
- The script does not delete trips, SOS events, or chat records after the run.
- Any failed step returns a non-zero exit code, which makes it suitable for quick CI checks or pre-demo verification.
