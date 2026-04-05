from __future__ import annotations

import json
import os
import re
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Optional
from urllib.parse import quote

import jwt
from fastapi import HTTPException, status
from sqlalchemy.orm import Session

from config import get_settings

try:  # pragma: no cover - boto3 is optional in local-dev fallback mode
    import boto3
except Exception:  # pragma: no cover - keep local fallback functional without boto3
    boto3 = None  # type: ignore[assignment]


UPLOAD_TOKEN_SCOPE = "sos_media_upload"
DEFAULT_CONTENT_TYPE = "application/octet-stream"
MEDIA_KEY_PATTERN = re.compile(r"/sos/media/([^/?#]+)")


@dataclass(slots=True)
class SosMediaPresignResult:
    media_key: str
    upload_url: str
    upload_method: str
    upload_headers: dict[str, str]
    playback_url: str
    audio_url: str
    storage_mode: str
    bucket: Optional[str]
    expires_in_seconds: int


@dataclass(slots=True)
class SosMediaCommitResult:
    media_key: str
    audio_url: str
    playback_url: str
    storage_mode: str
    bucket: Optional[str]
    content_type: Optional[str]
    size_bytes: Optional[int]


@dataclass(slots=True)
class SosMediaUploadResult:
    media_key: str
    stored_bytes: int
    audio_url: str
    message: str


@dataclass(slots=True)
class ResolvedMediaReference:
    media_key: Optional[str]
    audio_url: Optional[str]
    storage_mode: str
    bucket: Optional[str]
    content_type: Optional[str]
    size_bytes: Optional[int]


def _utc_now() -> datetime:
    return datetime.now(timezone.utc)


def normalize_filename(filename: Optional[str]) -> str:
    value = (filename or "sos-audio.m4a").strip()
    value = value.replace("\\", "/").split("/")[-1]
    value = re.sub(r"[^A-Za-z0-9._-]+", "-", value).strip("-._")
    return value or "sos-audio.m4a"


def build_media_key(*, user_id: int, trip_id: Optional[int], filename: Optional[str]) -> str:
    safe_name = normalize_filename(filename)
    stem, suffix = os.path.splitext(safe_name)
    suffix = suffix if suffix else ".m4a"
    timestamp = _utc_now().strftime("%Y%m%d%H%M%S")
    return f"sos-{user_id}-{trip_id or 'na'}-{timestamp}-{uuid.uuid4().hex}{suffix}"


def build_playback_url(base_url: str, media_key: str) -> str:
    return f"{base_url.rstrip('/')}/api/v2/sos/media/{quote(media_key, safe='')}"


def build_local_upload_token(
    *,
    media_key: str,
    user_id: int,
    trip_id: Optional[int],
    filename: Optional[str],
    content_type: Optional[str],
    size_bytes: Optional[int],
) -> str:
    settings = get_settings()
    payload: dict[str, Any] = {
        "scope": UPLOAD_TOKEN_SCOPE,
        "media_key": media_key,
        "user_id": user_id,
        "trip_id": trip_id,
        "filename": filename,
        "content_type": content_type,
        "size_bytes": size_bytes,
        "iat": int(_utc_now().timestamp()),
        "exp": int((_utc_now().timestamp()) + settings.sos_upload_token_ttl_seconds),
    }
    return jwt.encode(payload, settings.jwt_secret_key, algorithm="HS256")


def decode_local_upload_token(token: str) -> dict[str, Any]:
    settings = get_settings()
    payload = jwt.decode(token, settings.jwt_secret_key, algorithms=["HS256"])
    if payload.get("scope") != UPLOAD_TOKEN_SCOPE:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="无效的SOS上传令牌")
    return payload


def get_local_media_directory() -> Path:
    settings = get_settings()
    directory = Path(settings.sos_local_media_dir)
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def local_media_path(media_key: str) -> Path:
    return get_local_media_directory() / media_key


def local_media_meta_path(media_key: str) -> Path:
    return get_local_media_directory() / f"{media_key}.meta.json"


def determine_storage_mode() -> str:
    settings = get_settings()
    if settings.has_s3_media_storage and boto3 is not None:
        return "s3"
    return "local"


def build_presign_result(
    *,
    base_url: str,
    user_id: int,
    trip_id: Optional[int],
    filename: Optional[str],
    content_type: Optional[str],
    size_bytes: Optional[int],
) -> SosMediaPresignResult:
    settings = get_settings()
    storage_mode = determine_storage_mode()
    media_key = build_media_key(user_id=user_id, trip_id=trip_id, filename=filename)
    playback_url = build_playback_url(base_url, media_key)
    upload_headers: dict[str, str] = {}

    if storage_mode == "s3":
        if boto3 is None:
            raise HTTPException(
                status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                detail="S3媒体存储已配置，但当前环境缺少boto3",
            )
        client_kwargs: dict[str, Any] = {"region_name": settings.s3_region}
        if settings.s3_access_key_id and settings.s3_secret_access_key:
            client_kwargs["aws_access_key_id"] = settings.s3_access_key_id
            client_kwargs["aws_secret_access_key"] = settings.s3_secret_access_key
        if settings.s3_session_token:
            client_kwargs["aws_session_token"] = settings.s3_session_token
        if settings.s3_endpoint_url:
            client_kwargs["endpoint_url"] = settings.s3_endpoint_url

        client = boto3.client("s3", **client_kwargs)
        upload_headers["Content-Type"] = content_type or DEFAULT_CONTENT_TYPE
        upload_url = client.generate_presigned_url(
            "put_object",
            Params={
                "Bucket": settings.s3_bucket_name,
                "Key": media_key,
                "ContentType": upload_headers["Content-Type"],
            },
            ExpiresIn=settings.sos_upload_token_ttl_seconds,
        )
        return SosMediaPresignResult(
            media_key=media_key,
            upload_url=upload_url,
            upload_method="PUT",
            upload_headers=upload_headers,
            playback_url=playback_url,
            audio_url=playback_url,
            storage_mode=storage_mode,
            bucket=settings.s3_bucket_name,
            expires_in_seconds=settings.sos_upload_token_ttl_seconds,
        )

    upload_token = build_local_upload_token(
        media_key=media_key,
        user_id=user_id,
        trip_id=trip_id,
        filename=filename,
        content_type=content_type,
        size_bytes=size_bytes,
    )
    upload_url = f"{base_url.rstrip('/')}/api/v2/sos/media/{quote(media_key, safe='')}?upload_token={quote(upload_token, safe='')}"
    if content_type:
        upload_headers["Content-Type"] = content_type

    return SosMediaPresignResult(
        media_key=media_key,
        upload_url=upload_url,
        upload_method="PUT",
        upload_headers=upload_headers,
        playback_url=playback_url,
        audio_url=playback_url,
        storage_mode=storage_mode,
        bucket=None,
        expires_in_seconds=settings.sos_upload_token_ttl_seconds,
    )


def store_local_media(
    *,
    media_key: str,
    upload_token: str,
    body: bytes,
    content_type: Optional[str],
    base_url: str,
) -> SosMediaUploadResult:
    payload = decode_local_upload_token(upload_token)
    if payload.get("media_key") != media_key:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="上传令牌与媒体引用不匹配")

    expected_size = payload.get("size_bytes")
    if expected_size is not None and expected_size != len(body):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="上传内容大小与令牌不一致")

    path = local_media_path(media_key)
    meta_path = local_media_meta_path(media_key)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(body)
    meta_path.write_text(
        json.dumps(
            {
                "media_key": media_key,
                "user_id": payload.get("user_id"),
                "trip_id": payload.get("trip_id"),
                "filename": payload.get("filename"),
                "content_type": content_type or payload.get("content_type") or DEFAULT_CONTENT_TYPE,
                "stored_bytes": len(body),
                "created_at": _utc_now().isoformat(),
                "storage_mode": "local",
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    playback_url = build_playback_url(base_url, media_key)
    return SosMediaUploadResult(
        media_key=media_key,
        stored_bytes=len(body),
        audio_url=playback_url,
        message="SOS音频已上传",
    )


def resolve_local_media_metadata(media_key: str) -> dict[str, Any]:
    meta_path = local_media_meta_path(media_key)
    if not meta_path.exists():
        return {}
    try:
        data = json.loads(meta_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}
    return data if isinstance(data, dict) else {}


def resolve_media_reference(
    *,
    base_url: str,
    audio_url: Optional[str],
    media_key: Optional[str],
) -> ResolvedMediaReference:
    resolved_key = (media_key or "").strip() or extract_media_key(audio_url)
    settings = get_settings()
    playback_url = build_playback_url(base_url, resolved_key) if resolved_key else audio_url
    metadata = resolve_local_media_metadata(resolved_key) if resolved_key else {}
    storage_mode = metadata.get("storage_mode") or determine_storage_mode() if resolved_key else "legacy"
    return ResolvedMediaReference(
        media_key=resolved_key or None,
        audio_url=playback_url,
        storage_mode=storage_mode,
        bucket=settings.s3_bucket_name if storage_mode == "s3" else None,
        content_type=metadata.get("content_type"),
        size_bytes=metadata.get("stored_bytes"),
    )


def commit_media_reference(
    *,
    base_url: str,
    media_key: str,
    content_type: Optional[str],
    size_bytes: Optional[int],
) -> SosMediaCommitResult:
    settings = get_settings()
    storage_mode = determine_storage_mode()
    playback_url = build_playback_url(base_url, media_key)

    if storage_mode == "local":
        path = local_media_path(media_key)
        if not path.exists():
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SOS媒体尚未上传")
        meta = resolve_local_media_metadata(media_key)
        resolved_content_type = content_type or meta.get("content_type") or DEFAULT_CONTENT_TYPE
        resolved_size = size_bytes or meta.get("stored_bytes") or path.stat().st_size
        return SosMediaCommitResult(
            media_key=media_key,
            audio_url=playback_url,
            playback_url=playback_url,
            storage_mode=storage_mode,
            bucket=None,
            content_type=resolved_content_type,
            size_bytes=resolved_size,
        )

    if boto3 is None:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="当前环境无法验证S3媒体")

    client_kwargs: dict[str, Any] = {"region_name": settings.s3_region}
    if settings.s3_access_key_id and settings.s3_secret_access_key:
        client_kwargs["aws_access_key_id"] = settings.s3_access_key_id
        client_kwargs["aws_secret_access_key"] = settings.s3_secret_access_key
    if settings.s3_session_token:
        client_kwargs["aws_session_token"] = settings.s3_session_token
    if settings.s3_endpoint_url:
        client_kwargs["endpoint_url"] = settings.s3_endpoint_url
    client = boto3.client("s3", **client_kwargs)
    try:
        head = client.head_object(Bucket=settings.s3_bucket_name, Key=media_key)
    except Exception as exc:  # pragma: no cover - depends on live S3
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SOS媒体尚未上传") from exc
    resolved_content_type = content_type or head.get("ContentType") or DEFAULT_CONTENT_TYPE
    resolved_size = size_bytes or head.get("ContentLength")
    return SosMediaCommitResult(
        media_key=media_key,
        audio_url=playback_url,
        playback_url=playback_url,
        storage_mode=storage_mode,
        bucket=settings.s3_bucket_name,
        content_type=resolved_content_type,
        size_bytes=resolved_size,
    )


def extract_media_key(reference: Optional[str]) -> Optional[str]:
    if not reference:
        return None
    match = MEDIA_KEY_PATTERN.search(reference)
    return match.group(1) if match else None


def load_media_bytes(media_key: str) -> tuple[bytes, Optional[str], Optional[str]]:
    settings = get_settings()
    storage_mode = determine_storage_mode()
    if storage_mode == "s3":
        if boto3 is None:
            raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="当前环境无法读取S3媒体")
        client_kwargs: dict[str, Any] = {"region_name": settings.s3_region}
        if settings.s3_access_key_id and settings.s3_secret_access_key:
            client_kwargs["aws_access_key_id"] = settings.s3_access_key_id
            client_kwargs["aws_secret_access_key"] = settings.s3_secret_access_key
        if settings.s3_session_token:
            client_kwargs["aws_session_token"] = settings.s3_session_token
        if settings.s3_endpoint_url:
            client_kwargs["endpoint_url"] = settings.s3_endpoint_url
        client = boto3.client("s3", **client_kwargs)
        try:
            response = client.get_object(Bucket=settings.s3_bucket_name, Key=media_key)
        except Exception as exc:  # pragma: no cover - depends on live S3
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SOS媒体不存在") from exc
        body = response["Body"].read()
        content_type = response.get("ContentType")
        etag = response.get("ETag")
        if etag:
            etag = etag.strip('"')
        return body, content_type, etag

    path = local_media_path(media_key)
    if not path.exists():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="SOS媒体不存在")
    meta = resolve_local_media_metadata(media_key)
    return path.read_bytes(), meta.get("content_type"), None
