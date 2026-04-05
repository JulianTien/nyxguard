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


class GuardianLinkApiTestCase(unittest.TestCase):
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

    def register_user(self, suffix: str, phone_prefix: str = "139") -> dict:
        response = self.client.post(
            "/api/auth/register",
            json={
                "nickname": f"User{suffix}",
                "phone": f"{phone_prefix}0000{suffix.zfill(4)}",
                "password": "abc123",
            },
        )
        self.assertEqual(response.status_code, 200, response.text)
        return response.json()

    def auth_header(self, token: str) -> dict[str, str]:
        return {"Authorization": f"Bearer {token}"}

    def test_guardian_link_lifecycle_and_filters(self) -> None:
        traveler_auth = self.register_user("21", "139")
        guardian_auth = self.register_user("22", "138")

        traveler_token = traveler_auth["token"]
        guardian_token = guardian_auth["token"]
        guardian_phone = guardian_auth["user"]["phone"]

        invite = self.client.post(
            "/api/guardian-links/invite",
            headers=self.auth_header(traveler_token),
            json={"guardian_account": guardian_phone, "relationship": "朋友"},
        )
        self.assertEqual(invite.status_code, 200, invite.text)
        invite_payload = invite.json()
        self.assertEqual(invite_payload["status"], "pending")
        self.assertEqual(invite_payload["current_role"], "traveler")
        self.assertEqual(invite_payload["traveler_user"]["id"], traveler_auth["user"]["id"])
        self.assertEqual(invite_payload["guardian_user"]["id"], guardian_auth["user"]["id"])

        traveler_list = self.client.get(
            "/api/guardian-links?role=traveler&status=active",
            headers=self.auth_header(traveler_token),
        )
        self.assertEqual(traveler_list.status_code, 200, traveler_list.text)
        self.assertEqual(len(traveler_list.json()), 1)
        self.assertEqual(traveler_list.json()[0]["current_role"], "traveler")

        guardian_list = self.client.get(
            "/api/guardian-links?role=guardian&status=active",
            headers=self.auth_header(guardian_token),
        )
        self.assertEqual(guardian_list.status_code, 200, guardian_list.text)
        self.assertEqual(len(guardian_list.json()), 1)
        self.assertEqual(guardian_list.json()[0]["current_role"], "guardian")

        accepted = self.client.post(
            f"/api/guardian-links/{invite_payload['id']}/accept",
            headers=self.auth_header(guardian_token),
        )
        self.assertEqual(accepted.status_code, 200, accepted.text)
        accepted_payload = accepted.json()
        self.assertEqual(accepted_payload["status"], "accepted")
        self.assertIsNotNone(accepted_payload["accepted_at"])

        revoked = self.client.delete(
            f"/api/guardian-links/{invite_payload['id']}",
            headers=self.auth_header(traveler_token),
        )
        self.assertEqual(revoked.status_code, 200, revoked.text)
        revoked_payload = revoked.json()
        self.assertEqual(revoked_payload["status"], "revoked")
        self.assertIsNotNone(revoked_payload["revoked_at"])

        archived = self.client.get(
            "/api/guardian-links?status=revoked",
            headers=self.auth_header(traveler_token),
        )
        self.assertEqual(archived.status_code, 200, archived.text)
        self.assertEqual(len(archived.json()), 1)
        self.assertEqual(archived.json()[0]["status"], "revoked")

        reinvite = self.client.post(
            "/api/guardian-links/invite",
            headers=self.auth_header(traveler_token),
            json={"guardian_account": guardian_phone, "relationship": "家人"},
        )
        self.assertEqual(reinvite.status_code, 200, reinvite.text)
        reinvite_payload = reinvite.json()
        self.assertEqual(reinvite_payload["status"], "pending")
        self.assertEqual(reinvite_payload["relationship"], "家人")
        self.assertIsNone(reinvite_payload["accepted_at"])
        self.assertIsNone(reinvite_payload["revoked_at"])
        self.assertEqual(reinvite_payload["current_role"], "traveler")

        self.assertEqual(
            self.client.get(
                "/api/guardians",
                headers=self.auth_header(traveler_token),
            ).status_code,
            200,
        )

    def test_invite_rejects_unknown_or_self_account(self) -> None:
        auth = self.register_user("23", "137")
        token = auth["token"]

        unknown = self.client.post(
            "/api/guardian-links/invite",
            headers=self.auth_header(token),
            json={"guardian_account": "not-found@example.com"},
        )
        self.assertEqual(unknown.status_code, 404, unknown.text)

        self_invite = self.client.post(
            "/api/guardian-links/invite",
            headers=self.auth_header(token),
            json={"guardian_account": auth["user"]["phone"]},
        )
        self.assertEqual(self_invite.status_code, 400, self_invite.text)


if __name__ == "__main__":
    unittest.main()
