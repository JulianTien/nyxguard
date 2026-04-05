from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Optional

from pydantic import model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


BASE_DIR = Path(__file__).resolve().parent
LOCAL_SQLITE_URL = f"sqlite:///{BASE_DIR / 'nyxguard.db'}"


def normalize_database_url(url: Optional[str]) -> str:
    if not url:
        return LOCAL_SQLITE_URL
    if url.startswith("postgres://"):
        return url.replace("postgres://", "postgresql+psycopg://", 1)
    if url.startswith("postgresql://"):
        return url.replace("postgresql://", "postgresql+psycopg://", 1)
    return url


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=BASE_DIR / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    app_env: str = "development"
    log_level: str = "INFO"
    host: str = "0.0.0.0"
    port: int = 5001
    debug: bool = False
    jwt_secret_key: str = "nyxguard-jwt-secret"
    cors_allow_origins: str = "*"
    database_url: str = LOCAL_SQLITE_URL
    database_url_non_pooling: Optional[str] = None
    openai_api_key: Optional[str] = None
    openai_model: str = "gpt-4o-mini"
    openai_base_url: str = "https://api.openai.com/v1"
    s3_bucket_name: Optional[str] = None
    s3_region: Optional[str] = None
    s3_access_key_id: Optional[str] = None
    s3_secret_access_key: Optional[str] = None
    s3_session_token: Optional[str] = None
    s3_endpoint_url: Optional[str] = None
    s3_public_base_url: Optional[str] = None
    sos_local_media_dir: str = str(BASE_DIR / "sos_media")
    sos_upload_token_ttl_seconds: int = 900
    watchdog_timeout_grace_minutes: int = 5
    watchdog_location_loss_walk_seconds: int = 90
    watchdog_location_loss_ride_seconds: int = 45
    watchdog_alert_dedup_seconds: int = 60
    sos_duplicate_window_seconds: int = 20

    @model_validator(mode="after")
    def validate_runtime_contract(self) -> "Settings":
        if self.app_env == "development":
            return self

        normalized_database_url = normalize_database_url(self.database_url)
        if normalized_database_url.startswith("sqlite"):
            raise ValueError("DATABASE_URL must point to Postgres outside development")

        if not self.jwt_secret_key or self.jwt_secret_key == "nyxguard-jwt-secret":
            raise ValueError("JWT_SECRET_KEY must be set to a non-default value outside development")

        return self

    @property
    def runtime_database_url(self) -> str:
        return normalize_database_url(self.database_url)

    @property
    def migration_database_url(self) -> str:
        return normalize_database_url(self.database_url_non_pooling or self.database_url)

    @property
    def cors_origins(self) -> list[str]:
        if self.cors_allow_origins.strip() == "*":
            return ["*"]
        return [item.strip() for item in self.cors_allow_origins.split(",") if item.strip()]

    @property
    def should_auto_bootstrap_schema(self) -> bool:
        return self.app_env == "development" and self.runtime_database_url.startswith("sqlite")

    @property
    def has_s3_media_storage(self) -> bool:
        return bool(self.s3_bucket_name and self.s3_region)

    @property
    def storage_mode(self) -> str:
        return "s3" if self.has_s3_media_storage else "local"


@lru_cache
def get_settings() -> Settings:
    return Settings()
