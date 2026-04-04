"""Vercel entrypoint that keeps the deployment surface separate from server.py."""

from server import app

__all__ = ["app"]
