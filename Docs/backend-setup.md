# Backend Setup

## Local development

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python app.py
```

## Smoke test

From the repository root:

```bash
python scripts/api_smoke.py --profile local
```
