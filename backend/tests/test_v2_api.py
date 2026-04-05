from __future__ import annotations

import os
import tempfile
import unittest

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

import database
import main
from database import Base


class V2ApiTestCase(unittest.TestCase):
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

    def register_user(self, suffix: str = "01") -> dict:
        response = self.client.post(
            "/api/auth/register",
            json={
                "nickname": f"Tester{suffix}",
                "phone": f"1390000{suffix.zfill(4)}",
                "password": "abc123",
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def auth_header(self, token: str) -> dict[str, str]:
        return {"Authorization": f"Bearer {token}"}

    def test_v2_endpoints_and_fallback(self) -> None:
        auth = self.register_user("11")
        token = auth["token"]

        guardian = self.client.post(
            "/api/guardians",
            headers=self.auth_header(token),
            json={"nickname": "Alice", "phone": "13800001111", "relationship": "朋友"},
        )
        self.assertEqual(guardian.status_code, 200, guardian.text)
        guardian_id = guardian.json()["id"]

        trip = self.client.post(
            "/api/trips",
            headers=self.auth_header(token),
            json={
                "trip_type": "walk",
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
        self.assertEqual(trip.status_code, 200, trip.text)
        trip_id = trip.json()["id"]
        self.assertIsNotNone(trip.json()["expected_arrive_at"])

        dashboard = self.client.get("/api/v2/dashboard", headers=self.auth_header(token))
        self.assertEqual(dashboard.status_code, 200, dashboard.text)
        self.assertEqual(dashboard.json()["guardian_count"], 1)
        self.assertEqual(dashboard.json()["active_trip_brief"]["id"], trip_id)

        locations = self.client.post(
            f"/api/trips/{trip_id}/locations",
            headers=self.auth_header(token),
            json={
                "locations": [
                    {"lat": 31.2298, "lng": 121.4742, "accuracy": 6.5},
                    {"lat": 31.2289, "lng": 121.4751, "accuracy": 5.9},
                ]
            },
        )
        self.assertEqual(locations.status_code, 200, locations.text)

        current_trip = self.client.get("/api/v2/trips/current", headers=self.auth_header(token))
        self.assertEqual(current_trip.status_code, 200, current_trip.text)
        self.assertEqual(current_trip.json()["id"], trip_id)
        self.assertGreaterEqual(len(current_trip.json()["route_preview"]), 1)

        chat = self.client.post(
            "/api/v2/chat/messages",
            headers=self.auth_header(token),
            json={"content": "我有点害怕", "trip_id": trip_id},
        )
        self.assertEqual(chat.status_code, 200, chat.text)
        chat_payload = chat.json()
        self.assertTrue(chat_payload["assistant_message"]["content"])
        self.assertTrue(chat_payload["used_fallback"])

        history = self.client.get(
            f"/api/v2/chat/messages?trip_id={trip_id}",
            headers=self.auth_header(token),
        )
        self.assertEqual(history.status_code, 200, history.text)
        self.assertGreaterEqual(len(history.json()["messages"]), 2)

        proactive = self.client.post(
            "/api/v2/chat/proactive",
            headers=self.auth_header(token),
            json={"trigger": "deviation", "trip_id": trip_id},
        )
        self.assertEqual(proactive.status_code, 200, proactive.text)
        self.assertEqual(proactive.json()["message_type"], "proactive")

        alert = self.client.post(
            f"/api/v2/trips/{trip_id}/alerts",
            headers=self.auth_header(token),
            json={"alert_type": "walk_deviation", "lat": 31.2288, "lng": 121.4752},
        )
        self.assertEqual(alert.status_code, 200, alert.text)
        self.assertEqual(alert.json()["trip_id"], trip_id)
        self.assertEqual(alert.json()["guardian_count"], 1)
        self.assertTrue(alert.json()["proactive_message"])

        events = self.client.get("/api/notifications/events", headers=self.auth_header(token))
        self.assertEqual(events.status_code, 200, events.text)
        event_types = [item["event_type"] for item in events.json()]
        self.assertIn("trip_started", event_types)
        self.assertIn("walk_deviation", event_types)

        pushed = self.client.post(
            "/api/notifications/push",
            headers=self.auth_header(token),
            json={
                "event_type": "manual_push",
                "title": "测试通知",
                "body": "这是一条主动投递测试",
                "trip_id": trip_id,
                "guardian_id": guardian_id,
                "payload": {"channel": "manual"},
            },
        )
        self.assertEqual(pushed.status_code, 200, pushed.text)
        self.assertEqual(pushed.json()["event_type"], "manual_push")
        self.assertIn("delivery_status", pushed.json())
        self.assertIn("delivery_channel", pushed.json())
        self.assertIn(pushed.json()["delivery_status"], {"queued", "sent", "skipped", "failed", "opened"})

        summary = self.client.get("/api/v2/profile/summary", headers=self.auth_header(token))
        self.assertEqual(summary.status_code, 200, summary.text)
        self.assertEqual(summary.json()["guardian_count"], 1)

        trip_sos = self.client.post(
            "/api/v2/sos",
            headers=self.auth_header(token),
            json={"trip_id": trip_id, "lat": 31.2285, "lng": 121.4755},
        )
        self.assertEqual(trip_sos.status_code, 200, trip_sos.text)
        self.assertEqual(trip_sos.json()["linked_trip_id"], trip_id)

        finish = self.client.put(f"/api/trips/{trip_id}/finish", headers=self.auth_header(token))
        self.assertEqual(finish.status_code, 200, finish.text)

        standalone_sos = self.client.post(
            "/api/v2/sos",
            headers=self.auth_header(token),
            json={"lat": 31.2200, "lng": 121.4700},
        )
        self.assertEqual(standalone_sos.status_code, 200, standalone_sos.text)
        self.assertIsNone(standalone_sos.json()["linked_trip_id"])

    def test_push_token_lifecycle_and_open_receipt(self) -> None:
        auth = self.register_user("13")
        token = auth["token"]

        register_response = self.client.post(
            "/api/notifications/tokens",
            headers=self.auth_header(token),
            json={
                "token": "fcm-test-token-13",
                "platform": "android",
                "device_name": "Pixel Test",
                "app_version": "1.0.0",
            },
        )
        self.assertEqual(register_response.status_code, 200, register_response.text)
        self.assertTrue(register_response.json()["enabled"])
        self.assertEqual(register_response.json()["token"], "fcm-test-token-13")

        compat_register_response = self.client.post(
            "/api/push-tokens/register",
            headers=self.auth_header(token),
            json={"token": "fcm-compat-token-13", "platform": "android"},
        )
        self.assertEqual(compat_register_response.status_code, 200, compat_register_response.text)
        self.assertEqual(compat_register_response.json()["status"], "registered")

        push_response = self.client.post(
            "/api/notifications/push",
            headers=self.auth_header(token),
            json={
                "event_type": "manual_push",
                "title": "测试推送",
                "body": "这是一条推送状态机测试",
                "payload": {"source": "unit-test"},
            },
        )
        self.assertEqual(push_response.status_code, 200, push_response.text)
        push_json = push_response.json()
        self.assertIn(push_json["delivery_status"], {"skipped", "failed", "sent", "opened"})
        self.assertEqual(push_json["delivery_channel"], "fcm")

        events_response = self.client.get("/api/notifications/events", headers=self.auth_header(token))
        self.assertEqual(events_response.status_code, 200, events_response.text)
        events_json = events_response.json()
        self.assertGreaterEqual(len(events_json), 1)
        self.assertIn("delivery_status", events_json[0])
        self.assertIn("attempt_count", events_json[0])

        opened_response = self.client.post(
            f"/api/notifications/events/{push_json['id']}/opened",
            headers=self.auth_header(token),
        )
        self.assertEqual(opened_response.status_code, 200, opened_response.text)
        self.assertEqual(opened_response.json()["delivery_status"], "opened")
        self.assertIsNotNone(opened_response.json()["opened_at"])

        delete_response = self.client.delete(
            "/api/notifications/tokens",
            headers=self.auth_header(token),
            params={"token": "fcm-test-token-13"},
        )
        self.assertEqual(delete_response.status_code, 200, delete_response.text)

        compat_delete_response = self.client.post(
            "/api/push-tokens/deregister",
            headers=self.auth_header(token),
            json={"token": "fcm-compat-token-13"},
        )
        self.assertEqual(compat_delete_response.status_code, 200, compat_delete_response.text)
        self.assertIn("停用", delete_response.json()["message"])

    def test_cannot_delete_last_guardian(self) -> None:
        auth = self.register_user("12")
        token = auth["token"]

        guardian = self.client.post(
            "/api/guardians",
            headers=self.auth_header(token),
            json={"nickname": "OnlyOne", "phone": "13800001234", "relationship": "朋友"},
        )
        self.assertEqual(guardian.status_code, 200, guardian.text)

        delete_response = self.client.delete(
            f"/api/guardians/{guardian.json()['id']}",
            headers=self.auth_header(token),
        )
        self.assertEqual(delete_response.status_code, 400, delete_response.text)
        self.assertIn("至少保留1名守护者", delete_response.json()["detail"])


if __name__ == "__main__":
    unittest.main()
