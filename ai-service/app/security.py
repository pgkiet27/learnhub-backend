"""Caller identity from the X-User-Id / X-User-Role headers set by the API Gateway."""
import logging
import uuid
from dataclasses import dataclass

import httpx
from fastapi import Header

from app.api_response import ApiError
from app.config import settings

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class CurrentUser:
    user_id: str
    role: str | None


def current_user(
    x_user_id: str | None = Header(None),
    x_user_role: str | None = Header(None),
) -> CurrentUser:
    try:
        user_id = str(uuid.UUID(x_user_id or ""))
    except ValueError:
        raise ApiError(401, "UNAUTHORIZED", "Bạn cần đăng nhập để sử dụng tính năng này")
    return CurrentUser(user_id=user_id, role=(x_user_role or "").lower() or None)


def require_enrollment(user: CurrentUser, course_id: str) -> None:
    if not settings.ENROLLMENT_CHECK_ENABLED or user.role == "admin":
        return

    headers = {"X-User-Id": user.user_id}
    if user.role:
        headers["X-User-Role"] = user.role
    try:
        resp = httpx.get(
            f"{settings.ENROLLMENT_SERVICE_URL}/api/v1/enrollments/{course_id}/status",
            headers=headers,
            timeout=5.0,
        )
        resp.raise_for_status()
        enrolled = bool((resp.json().get("data") or {}).get("enrolled"))
    except (httpx.HTTPError, ValueError) as e:
        log.warning("Enrollment check failed for user %s, course %s: %s", user.user_id, course_id, e)
        raise ApiError(
            503, "ENROLLMENT_CHECK_FAILED",
            "Không kiểm tra được trạng thái đăng ký khóa học, vui lòng thử lại sau",
        )

    if not enrolled:
        raise ApiError(403, "NOT_ENROLLED", "Bạn cần đăng ký khóa học để sử dụng trợ lý AI")
