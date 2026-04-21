# NyxGuard Backend

This directory contains the FastAPI backend for NyxGuard.

## Run locally

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python app.py
```

Alternative:

```bash
uvicorn server:app --host 0.0.0.0 --port 5001 --reload
```
