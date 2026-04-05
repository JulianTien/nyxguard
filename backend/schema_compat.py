from __future__ import annotations

from sqlalchemy import inspect, text
from sqlalchemy.engine import Engine


def _timestamp_type(engine: Engine) -> str:
    return "TIMESTAMP WITH TIME ZONE" if engine.dialect.name == "postgresql" else "DATETIME"


def _existing_columns(engine: Engine, table_name: str) -> set[str]:
    inspector = inspect(engine)
    if table_name not in inspector.get_table_names():
        return set()
    return {column["name"] for column in inspector.get_columns(table_name)}


def _execute_statements(engine: Engine, statements: list[str]) -> None:
    if not statements:
        return
    with engine.begin() as conn:
        for statement in statements:
            conn.execute(text(statement))


def ensure_user_profile_columns(engine: Engine) -> None:
    existing_columns = _existing_columns(engine, "ng_user")
    if not existing_columns:
        return

    alter_statements: list[str] = []
    updated_at_type = _timestamp_type(engine)

    if "avatar_url" not in existing_columns:
        alter_statements.append("ALTER TABLE ng_user ADD COLUMN avatar_url VARCHAR(500)")
    if "emergency_phone" not in existing_columns:
        alter_statements.append("ALTER TABLE ng_user ADD COLUMN emergency_phone VARCHAR(20)")
    if "updated_at" not in existing_columns:
        alter_statements.append(f"ALTER TABLE ng_user ADD COLUMN updated_at {updated_at_type}")

    _execute_statements(engine, alter_statements)

    if alter_statements:
        with engine.begin() as conn:
            conn.execute(
                text(
                    "UPDATE ng_user "
                    "SET updated_at = COALESCE(updated_at, created_at) "
                    "WHERE updated_at IS NULL"
                )
            )


def ensure_trip_columns(engine: Engine) -> None:
    existing_columns = _existing_columns(engine, "ng_trip")
    if not existing_columns:
        return

    expected_arrive_at_type = _timestamp_type(engine)
    statements: list[str] = []
    if "expected_arrive_at" not in existing_columns:
        statements.append(f"ALTER TABLE ng_trip ADD COLUMN expected_arrive_at {expected_arrive_at_type}")

    _execute_statements(engine, statements)

    if "expected_arrive_at" not in existing_columns:
        with engine.begin() as conn:
            conn.execute(
                text(
                    "UPDATE ng_trip "
                    "SET expected_arrive_at = DATETIME(created_at, '+' || estimated_minutes || ' minutes') "
                    "WHERE expected_arrive_at IS NULL"
                )
                if engine.dialect.name == "sqlite"
                else text(
                    "UPDATE ng_trip "
                    "SET expected_arrive_at = created_at + (estimated_minutes || ' minutes')::interval "
                    "WHERE expected_arrive_at IS NULL"
                )
            )


def ensure_sos_columns(engine: Engine) -> None:
    inspector = inspect(engine)
    if "ng_sos_event" not in inspector.get_table_names():
        return
    columns = inspector.get_columns("ng_sos_event")
    existing_columns = {column["name"] for column in columns}
    trip_column = next((column for column in columns if column["name"] == "trip_id"), None)
    trip_not_nullable = bool(trip_column) and trip_column.get("nullable") is False

    statements: list[str] = []
    if "user_id" not in existing_columns:
        statements.append("ALTER TABLE ng_sos_event ADD COLUMN user_id INTEGER")
    if "audio_media_key" not in existing_columns:
        statements.append("ALTER TABLE ng_sos_event ADD COLUMN audio_media_key VARCHAR(200)")
    if "audio_bucket" not in existing_columns:
        statements.append("ALTER TABLE ng_sos_event ADD COLUMN audio_bucket VARCHAR(100)")
    if "audio_content_type" not in existing_columns:
        statements.append("ALTER TABLE ng_sos_event ADD COLUMN audio_content_type VARCHAR(100)")
    if "audio_etag" not in existing_columns:
        statements.append("ALTER TABLE ng_sos_event ADD COLUMN audio_etag VARCHAR(200)")
    if "audio_size_bytes" not in existing_columns:
        statements.append("ALTER TABLE ng_sos_event ADD COLUMN audio_size_bytes INTEGER")
    if "audio_storage_mode" not in existing_columns:
        statements.append(
            "ALTER TABLE ng_sos_event ADD COLUMN audio_storage_mode VARCHAR(20) NOT NULL DEFAULT 'legacy'"
        )
    if "audio_uploaded_at" not in existing_columns:
        statements.append("ALTER TABLE ng_sos_event ADD COLUMN audio_uploaded_at DATETIME")

    _execute_statements(engine, statements)

    if "user_id" not in existing_columns:
        with engine.begin() as conn:
            conn.execute(
                text(
                    "UPDATE ng_sos_event "
                    "SET user_id = ("
                    "  SELECT ng_trip.user_id FROM ng_trip WHERE ng_trip.id = ng_sos_event.trip_id"
                    ") "
                    "WHERE user_id IS NULL AND trip_id IS NOT NULL"
                )
            )

    if "audio_storage_mode" not in existing_columns:
        with engine.begin() as conn:
            conn.execute(
                text(
                    "UPDATE ng_sos_event "
                    "SET audio_storage_mode = 'legacy' "
                    "WHERE audio_storage_mode IS NULL"
                )
            )

    if trip_not_nullable:
        if engine.dialect.name == "sqlite":
            with engine.begin() as conn:
                conn.execute(
                    text(
                        "CREATE TABLE IF NOT EXISTS ng_sos_event_new ("
                        "id INTEGER PRIMARY KEY, "
                        "user_id INTEGER NOT NULL, "
                        "trip_id INTEGER, "
                        "lat FLOAT NOT NULL, "
                        "lng FLOAT NOT NULL, "
                        "audio_url VARCHAR(500), "
                        "status VARCHAR(20) NOT NULL DEFAULT 'active', "
                        "created_at DATETIME NOT NULL, "
                        "FOREIGN KEY(user_id) REFERENCES ng_user(id), "
                        "FOREIGN KEY(trip_id) REFERENCES ng_trip(id)"
                        ")"
                    )
                )
                conn.execute(
                    text(
                        "INSERT INTO ng_sos_event_new (id, user_id, trip_id, lat, lng, audio_url, status, created_at) "
                        "SELECT id, user_id, trip_id, lat, lng, audio_url, status, created_at "
                        "FROM ng_sos_event"
                    )
                )
                conn.execute(text("DROP TABLE ng_sos_event"))
                conn.execute(text("ALTER TABLE ng_sos_event_new RENAME TO ng_sos_event"))
                conn.execute(text("CREATE INDEX IF NOT EXISTS ix_ng_sos_event_id ON ng_sos_event(id)"))
                conn.execute(text("CREATE INDEX IF NOT EXISTS ix_ng_sos_event_trip_id ON ng_sos_event(trip_id)"))
                conn.execute(text("CREATE INDEX IF NOT EXISTS ix_ng_sos_event_user_id ON ng_sos_event(user_id)"))
        elif engine.dialect.name == "postgresql":
            with engine.begin() as conn:
                conn.execute(text("ALTER TABLE ng_sos_event ALTER COLUMN trip_id DROP NOT NULL"))


def ensure_notification_columns(engine: Engine) -> None:
    inspector = inspect(engine)
    if "ng_notification_event" not in inspector.get_table_names():
        return

    existing_columns = {column["name"] for column in inspector.get_columns("ng_notification_event")}
    statements: list[str] = []
    if "delivery_channel" not in existing_columns:
        statements.append("ALTER TABLE ng_notification_event ADD COLUMN delivery_channel VARCHAR(20) DEFAULT 'fcm'")
    if "delivery_status" not in existing_columns:
        statements.append("ALTER TABLE ng_notification_event ADD COLUMN delivery_status VARCHAR(20) DEFAULT 'queued'")
    if "attempt_count" not in existing_columns:
        statements.append("ALTER TABLE ng_notification_event ADD COLUMN attempt_count INTEGER DEFAULT 0")
    if "delivered_at" not in existing_columns:
        statements.append(
            f"ALTER TABLE ng_notification_event ADD COLUMN delivered_at {_timestamp_type(engine)}"
        )
    if "opened_at" not in existing_columns:
        statements.append(
            f"ALTER TABLE ng_notification_event ADD COLUMN opened_at {_timestamp_type(engine)}"
        )
    if "failure_reason" not in existing_columns:
        statements.append("ALTER TABLE ng_notification_event ADD COLUMN failure_reason VARCHAR(500)")

    _execute_statements(engine, statements)

    if statements:
        with engine.begin() as conn:
            if "delivery_channel" not in existing_columns:
                conn.execute(
                    text(
                        "UPDATE ng_notification_event "
                        "SET delivery_channel = 'fcm'"
                    )
                )
            if "delivery_status" not in existing_columns:
                conn.execute(
                    text(
                        "UPDATE ng_notification_event "
                        "SET delivery_status = COALESCE(status, 'queued')"
                    )
                )
            if "attempt_count" not in existing_columns:
                conn.execute(
                    text(
                        "UPDATE ng_notification_event "
                        "SET attempt_count = 0"
                    )
                )
            conn.execute(
                text(
                    "UPDATE ng_notification_event "
                    "SET status = COALESCE(status, 'queued') "
                    "WHERE status IS NULL"
                )
            )


def ensure_schema_compatibility(engine: Engine) -> None:
    ensure_user_profile_columns(engine)
    ensure_trip_columns(engine)
    ensure_sos_columns(engine)
    ensure_notification_columns(engine)
