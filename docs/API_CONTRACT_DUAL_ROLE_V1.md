# NyxGuard Dual-Role API Contract (Frozen v1)

Date: 2026-04-06

This contract is the implementation baseline for Android + FastAPI dual-role beta.

## 1) Guardian Link APIs

- `POST /api/guardian-links/invite`
  - request: `{ "guardian_user_id": number, "relationship": string? }`
  - response: `{ "id": number, "traveler_user_id": number, "guardian_user_id": number, "status": "invited"|"accepted"|"revoked", "relationship": string, "invited_at": string, "accepted_at": string|null, "revoked_at": string|null }`

- `POST /api/guardian-links/{id}/accept`
  - response: `GuardianLinkRead`

- `GET /api/guardian-links/mine/as-traveler`
  - response: `GuardianLinkRead[]`

- `GET /api/guardian-links/mine/as-guardian`
  - response: `GuardianLinkRead[]`

- `DELETE /api/guardian-links/{id}`
  - response: `{ "message": "..." }`

## 2) Push Token APIs

- `POST /api/notifications/push-tokens/register`
  - request: `{ "provider": "fcm", "token": string, "device_id": string?, "device_name": string?, "app_version": string?, "platform": "android" }`
  - response: `{ "message": string }`

- `POST /api/notifications/push-tokens/deregister`
  - request: `{ "provider": "fcm", "token": string }`
  - response: `{ "message": string }`

## 3) Notification Event Delivery Fields

`NotificationEventRead` adds:

- `delivery_channel`: string (`push` by default)
- `delivery_status`: string (`queued|sent|delivered|opened|failed`)
- `attempt_count`: int
- `delivered_at`: string|null
- `opened_at`: string|null
- `failure_reason`: string|null

## 4) SOS Media APIs

- `POST /api/v2/sos/media/presign`
  - request: `{ "content_type": string, "file_name": string? }`
  - response: `{ "upload_url": string, "method": "PUT", "headers": object, "media_key": string, "expires_in": int }`

- `POST /api/v2/sos/media/commit`
  - request: `{ "media_key": string, "content_type": string?, "duration_seconds": number? }`
  - response: `{ "media_key": string, "playback_url": string|null }`

- `POST /api/v2/sos`
  - request: `{ "trip_id": number|null, "lat": number, "lng": number, "media_key": string|null, "audio_url": string|null }`
  - response keeps legacy fields and adds `media_key` if available.

## 5) Guardian Mode APIs

- `GET /api/v2/guardian/dashboard`
- `GET /api/v2/guardian/trips/{trip_id}`
- `GET /api/v2/guardian/sos/latest?trip_id={id}`

Response detail fields are implementation-defined but must include trip id, status, latest location, and recent events.

## 6) Compatibility

- Existing `/api/guardians` remains available for compatibility.
- Existing `audio_url` in SOS stays optional in v1 transition; `media_key` is preferred.
