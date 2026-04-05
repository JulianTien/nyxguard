from __future__ import annotations

import os
import tempfile
import unittest
from datetime import timedelta, timezone
from urllib.parse import urlparse

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

import database
import main
from database import Base
from models import NotificationEvent, Trip
from services.watchdog import evaluate_all_watchdogs


class SosMediaAndWatchdogTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.temp_dir = tempfile.TemporaryDirectory()
        cls.db_path = os.path.join(cls.temp_dir.name, "test.db")
        cls.engine = create_engine(
            f"sqlite:///{cls.db_path}",
            connect_args={"check_same_thread": False},
        )
        cls.SessionLocal = sessionmaker(bind=cls.engine, autocommit=False, autoflush=False, class_=Session)
        Base.metadata.create_all(bind=cls.engine)

        def override_get_db():
            db = cls.SessionLocal()
            try:
                yield db
            finally:
                db.close()

        cls.override_get_db = override_get_db
        main.app.dependency_overrides[database.get_db] = override_get_db
        main.engine = cls.engine
        cls.client = TestClient(main.app)

    @classmethod
    def tearDownClass(cls) -> None:
        main.app.dependency_overrides.clear()
        cls.client.close()
        cls.engine.dispose()
        cls.temp_dir.cleanup()

    def register_user(self, suffix: str) -> dict:
        response = self.client.post(
            "/api/auth/register",
            json={
                "nickname": f"Tester{suffix}",
                "phone": f"1391000{suffix.zfill(4)}",
                "password": "abc123",
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def auth_header(self, token: str) -> dict[str, str]:
        return {"Authorization": f"Bearer {token}"}

    def create_trip(self, token: str, guardian_id: int, trip_type: str = "walk") -> int:
        response = self.client.post(
            "/api/trips",
            headers=self.auth_header(token),
            json={
                "trip_type": trip_type,
                "start_lat": 31.2304,
                "start_lng": 121.4737,
                "start_name": "People's Square",
                "end_lat": 31.2243,
                "end_lng": 121.4768,
                "end_name": "Xintiandi",
                "estimated_minutes": 18,
                "guardian_ids": [guardian_id],
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()["id"]

    def test_presign_commit_trigger_and_playback_uses_media_key(self) -> None:
        auth = self.register_user("21")
        token = auth["token"]
        guardian = self.client.post(
            "/api/guardians",
            headers=self.auth_header(token),
            json={"nickname": "Alice", "phone": "13800002111", "relationship": "朋友"},
        )
        self.assertEqual(guardian.status_code, 200, guardian.text)
        trip_id = self.create_trip(token, guardian.json()["id"])

        presign = self.client.post(
            "/api/v2/sos/media/presign",
            headers=self.auth_header(token),
            json={"trip_id": trip_id, "filename": "panic.m4a", "content_type": "audio/m4a", "size_bytes": 11},
        )
        self.assertEqual(presign.status_code, 200, presign.text)
        presign_payload = presign.json()
        self.assertTrue(presign_payload["media_key"])
        self.assertIn("/api/v2/sos/media/", presign_payload["upload_url"])

        upload_url = urlparse(presign_payload["upload_url"])
        upload_response = self.client.put(
            f"{upload_url.path}?{upload_url.query}",
            content=b"hello-world",
            headers={"Content-Type": "audio/m4a"},
        )
        self.assertEqual(upload_response.status_code, 200, upload_response.text)
        self.assertEqual(upload_response.json()["media_key"], presign_payload["media_key"])

        commit = self.client.post(
            "/api/v2/sos/media/commit",
            headers=self.auth_header(token),
            json={
                "trip_id": trip_id,
                "media_key": presign_payload["media_key"],
                "content_type": "audio/m4a",
                "size_bytes": 11,
            },
        )
        self.assertEqual(commit.status_code, 200, commit.text)
        commit_payload = commit.json()
        self.assertEqual(commit_payload["media_key"], presign_payload["media_key"])
        self.assertIn("/api/v2/sos/media/", commit_payload["audio_url"])

        trigger = self.client.post(
            "/api/v2/sos",
            headers=self.auth_header(token),
            json={
                "trip_id": trip_id,
                "lat": 31.2285,
                "lng": 121.4755,
                "media_key": presign_payload["media_key"],
            },
        )
        self.assertEqual(trigger.status_code, 200, trigger.text)
        trigger_payload = trigger.json()
        self.assertEqual(trigger_payload["linked_trip_id"], trip_id)
        self.assertEqual(trigger_payload["media_key"], presign_payload["media_key"])

        legacy_trigger = self.client.post(
            f"/api/trips/{trip_id}/sos",
            headers=self.auth_header(token),
            json={
                "lat": 31.2285,
                "lng": 121.4755,
                "media_key": presign_payload["media_key"],
            },
        )
        self.assertEqual(legacy_trigger.status_code, 200, legacy_trigger.text)
        self.assertEqual(legacy_trigger.json()["sos_id"], trigger_payload["sos_id"])

        playback = self.client.get(
            urlparse(commit_payload["audio_url"]).path,
            headers=self.auth_header(token),
        )
        self.assertEqual(playback.status_code, 200, playback.text)
        self.assertEqual(playback.content, b"hello-world")

        db = self.SessionLocal()
        try:
            sos_event = db.query(NotificationEvent).filter(NotificationEvent.event_type == "sos_triggered").first()
            self.assertIsNotNone(sos_event)
        finally:
            db.close()

    def test_duplicate_alert_is_suppressed(self) -> None:
        auth = self.register_user("22")
        token = auth["token"]
        guardian = self.client.post(
            "/api/guardians",
            headers=self.auth_header(token),
            json={"nickname": "Bob", "phone": "13800002222", "relationship": "朋友"},
        )
        self.assertEqual(guardian.status_code, 200, guardian.text)
        trip_id = self.create_trip(token, guardian.json()["id"])

        first = self.client.post(
            f"/api/v2/trips/{trip_id}/alerts",
            headers=self.auth_header(token),
            json={"alert_type": "walk_deviation", "lat": 31.2288, "lng": 121.4752},
        )
        self.assertEqual(first.status_code, 200, first.text)

        second = self.client.post(
            f"/api/v2/trips/{trip_id}/alerts",
            headers=self.auth_header(token),
            json={"alert_type": "walk_deviation", "lat": 31.2288, "lng": 121.4752},
        )
        self.assertEqual(second.status_code, 200, second.text)

        db = self.SessionLocal()
        try:
            event_count = (
                db.query(NotificationEvent)
                .filter(NotificationEvent.event_type == "walk_deviation", NotificationEvent.trip_id == trip_id)
                .count()
            )
        finally:
            db.close()
        self.assertEqual(event_count, 1)

    def test_watchdog_emits_location_loss_without_locations(self) -> None:
        auth = self.register_user("23")
        token = auth["token"]
        guardian = self.client.post(
            "/api/guardians",
            headers=self.auth_header(token),
            json={"nickname": "Cathy", "phone": "13800002333", "relationship": "朋友"},
        )
        self.assertEqual(guardian.status_code, 200, guardian.text)
        trip_id = self.create_trip(token, guardian.json()["id"], trip_type="ride")

        db = self.SessionLocal()
        try:
            trip = db.query(Trip).filter(Trip.id == trip_id).first()
            self.assertIsNotNone(trip)
            assert trip is not None
            trip.created_at = trip.created_at - timedelta(minutes=10)
            trip.expected_arrive_at = trip.created_at + timedelta(minutes=18)
            db.add(trip)
            db.commit()

            events = evaluate_all_watchdogs(
                db,
                now=trip.created_at.replace(tzinfo=timezone.utc) + timedelta(minutes=10, seconds=120),
            )
            db.commit()
            self.assertTrue(events)
            event_types = [
                item.event_type
                for item in db.query(NotificationEvent)
                .filter(NotificationEvent.trip_id == trip_id)
                .order_by(NotificationEvent.id.asc())
                .all()
            ]
            self.assertIn("location_loss", event_types)
        finally:
            db.close()


if __name__ == "__main__":
    unittest.main()
