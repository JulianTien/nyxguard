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


class GuardianModeApiTestCase(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.temp_dir = tempfile.TemporaryDirectory()
        cls.db_path = os.path.join(cls.temp_dir.name, "test_guardian_mode.db")
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

        main.app.dependency_overrides[database.get_db] = override_get_db
        main.engine = cls.engine
        cls.client = TestClient(main.app)

    @classmethod
    def tearDownClass(cls) -> None:
        main.app.dependency_overrides.clear()
        cls.client.close()
        cls.engine.dispose()
        cls.temp_dir.cleanup()

    def auth_header(self, token: str) -> dict[str, str]:
        return {"Authorization": f"Bearer {token}"}

    def register_user(self, suffix: str, nickname: str) -> dict:
        response = self.client.post(
            "/api/auth/register",
            json={
                "nickname": nickname,
                "phone": f"1370000{suffix.zfill(4)}",
                "password": "abc123",
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def test_guardian_dashboard_trip_and_sos_detail(self) -> None:
        traveler_auth = self.register_user("31", "Traveler31")
        guardian_auth = self.register_user("32", "Guardian32")
        traveler_token = traveler_auth["token"]
        guardian_token = guardian_auth["token"]

        invite = self.client.post(
            "/api/guardian-links/invite",
            headers=self.auth_header(traveler_token),
            json={"guardian_account": guardian_auth["user"]["phone"], "relationship": "朋友"},
        )
        self.assertEqual(invite.status_code, 200, invite.text)
        link_id = invite.json()["id"]

        accept = self.client.post(
            f"/api/guardian-links/{link_id}/accept",
            headers=self.auth_header(guardian_token),
        )
        self.assertEqual(accept.status_code, 200, accept.text)

        guardian_contact = self.client.post(
            "/api/guardians",
            headers=self.auth_header(traveler_token),
            json={
                "nickname": "LegacyGuardian",
                "phone": guardian_auth["user"]["phone"],
                "relationship": "朋友",
            },
        )
        self.assertEqual(guardian_contact.status_code, 200, guardian_contact.text)

        trip = self.client.post(
            "/api/trips",
            headers=self.auth_header(traveler_token),
            json={
                "trip_type": "walk",
                "start_lat": 31.2304,
                "start_lng": 121.4737,
                "start_name": "A",
                "end_lat": 31.2243,
                "end_lng": 121.4768,
                "end_name": "B",
                "estimated_minutes": 20,
                "guardian_ids": [guardian_contact.json()["id"]],
            },
        )
        self.assertEqual(trip.status_code, 200, trip.text)
        trip_id = trip.json()["id"]

        self.client.post(
            f"/api/trips/{trip_id}/locations",
            headers=self.auth_header(traveler_token),
            json={"locations": [{"lat": 31.2298, "lng": 121.4742, "accuracy": 6.5}]},
        )

        sos = self.client.post(
            "/api/v2/sos",
            headers=self.auth_header(traveler_token),
            json={"trip_id": trip_id, "lat": 31.2298, "lng": 121.4742},
        )
        self.assertEqual(sos.status_code, 200, sos.text)
        sos_id = sos.json()["sos_id"]

        dashboard = self.client.get("/api/v2/guardian/dashboard", headers=self.auth_header(guardian_token))
        self.assertEqual(dashboard.status_code, 200, dashboard.text)
        payload = dashboard.json()
        self.assertEqual(payload["guardian_user_id"], guardian_auth["user"]["id"])
        self.assertGreaterEqual(len(payload["protected_users"]), 1)

        trip_detail = self.client.get(f"/api/v2/guardian/trips/{trip_id}", headers=self.auth_header(guardian_token))
        self.assertEqual(trip_detail.status_code, 200, trip_detail.text)
        self.assertEqual(trip_detail.json()["trip_id"], trip_id)

        sos_detail = self.client.get(f"/api/v2/guardian/sos/{sos_id}", headers=self.auth_header(guardian_token))
        self.assertEqual(sos_detail.status_code, 200, sos_detail.text)
        self.assertEqual(sos_detail.json()["sos_id"], sos_id)


if __name__ == "__main__":
    unittest.main()
