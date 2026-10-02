"""Same response envelope as the Java services' ApiResponse."""
import logging
from datetime import datetime, timezone
from typing import Any, Generic, TypeVar

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from google.genai.errors import ClientError, ServerError
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel
from starlette.exceptions import HTTPException as StarletteHTTPException

log = logging.getLogger(__name__)

T = TypeVar("T")


class CamelModel(BaseModel):
    """camelCase JSON; snake_case still accepted on input."""
    model_config = ConfigDict(
        alias_generator=to_camel, populate_by_name=True, protected_namespaces=()
    )


class ErrorDetail(CamelModel):
    code: str
    message: str
    details: Any | None = None


class ApiResponse(CamelModel, Generic[T]):
    success: bool
    message: str | None = None
    data: T | None = None
    error: ErrorDetail | None = None
    timestamp: datetime


class ApiError(Exception):
    def __init__(self, status_code: int, code: str, message: str, details: Any = None):
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message
        self.details = details


def ok(data: Any = None, message: str = "OK") -> ApiResponse:
    return ApiResponse(success=True, message=message, data=data, timestamp=datetime.now(timezone.utc))


def error_response(status_code: int, code: str, message: str, details: Any = None) -> JSONResponse:
    body = ApiResponse(
        success=False,
        message=message,
        error=ErrorDetail(code=code, message=message, details=details),
        timestamp=datetime.now(timezone.utc),
    )
    return JSONResponse(
        status_code=status_code,
        content=body.model_dump(mode="json", by_alias=True, exclude_none=True),
    )


_HTTP_CODES = {401: "UNAUTHORIZED", 403: "FORBIDDEN", 404: "NOT_FOUND", 405: "METHOD_NOT_ALLOWED"}


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(ApiError)
    async def handle_api_error(_: Request, exc: ApiError):
        return error_response(exc.status_code, exc.code, exc.message, exc.details)

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(_: Request, exc: RequestValidationError):
        details = [
            {"field": ".".join(str(p) for p in e["loc"][1:]), "message": e["msg"]}
            for e in exc.errors()
        ]
        return error_response(400, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", details)

    @app.exception_handler(StarletteHTTPException)
    async def handle_http_error(_: Request, exc: StarletteHTTPException):
        code = _HTTP_CODES.get(exc.status_code, "HTTP_ERROR")
        return error_response(exc.status_code, code, str(exc.detail))

    @app.exception_handler(Exception)
    async def handle_unexpected(_: Request, exc: Exception):
        if isinstance(exc, ClientError) and exc.code == 429:
            log.warning("LLM quota exhausted: %s", exc)
            return error_response(
                503, "AI_QUOTA_EXCEEDED", "Trợ lý AI đã hết lượt sử dụng hôm nay, vui lòng thử lại sau"
            )
        if isinstance(exc, ServerError):
            log.warning("LLM provider unavailable: %s", exc)
            return error_response(
                503, "AI_UNAVAILABLE", "Trợ lý AI đang quá tải, vui lòng thử lại sau ít phút"
            )
        log.exception("Unhandled error")
        return error_response(500, "AI_SERVICE_ERROR", "Dịch vụ AI đang gặp sự cố, vui lòng thử lại sau")
