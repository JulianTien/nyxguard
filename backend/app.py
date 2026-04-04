"""Compatibility launcher for the FastAPI backend."""

import os

import uvicorn

from config import get_settings
from main import app


if __name__ == "__main__":
    settings = get_settings()
    uvicorn.run(
        "server:app",
        host="0.0.0.0",
        port=int(os.getenv("PORT", "5001")),
        reload=settings.app_env != "production",
        log_level=settings.log_level.lower(),
    )
