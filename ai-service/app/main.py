"""
FastAPI wrapper — expose RAG Chatbot + Churn Prediction thành REST API.
/api/v1/ai/** đi qua API Gateway; /api/v1/internal/ai/** chỉ dành cho service-to-service.

Chạy: uvicorn app.main:app --reload --port 8000
Swagger UI tự động có sẵn tại: http://localhost:8000/docs
"""
import logging
from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Depends, FastAPI, Query
from pydantic import Field

from app.api_response import ApiError, ApiResponse, CamelModel, ok, register_exception_handlers
from app.chat_history import get_latest_conversation
from app.db import get_connection
from app.indexing import index_lesson
from app.rag_query import ask_chatbot
from app.security import CurrentUser, current_user, require_enrollment
from app.summarize import get_lesson_course_id, summarize_lesson
from churn_prediction.predict import predict_churn

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")

app = FastAPI(
    title="LearnHub AI Service",
    description="API cho RAG Chatbot (hỏi-đáp, tóm tắt) và Churn Prediction",
    version="0.3.0",
)
register_exception_handlers(app)

public = APIRouter(prefix="/api/v1/ai", tags=["AI (public)"])
internal = APIRouter(prefix="/api/v1/internal/ai", tags=["AI (internal)"])


# ---------- Schemas ----------

class ChatRequest(CamelModel):
    course_id: UUID = Field(..., description="UUID của khóa học đang học")
    question: str = Field(..., min_length=1, max_length=2000, description="Câu hỏi của học viên")
    lesson_id: UUID | None = Field(None, description="Bài học đang mở (optional), được ưu tiên khi tìm ngữ cảnh")


class ChatSource(CamelModel):
    lesson_id: str
    chunk_index: int
    text_preview: str
    similarity: float


class ChatResponse(CamelModel):
    answer: str
    sources: list[ChatSource]


class ChatHistoryMessage(CamelModel):
    role: str
    content: str
    sources: list[ChatSource] | None = None
    created_at: datetime


class SummarizeResponse(CamelModel):
    lesson_id: str
    summary: str


class IndexLessonRequest(CamelModel):
    lesson_id: UUID = Field(..., description="UUID của bài học")
    course_id: UUID = Field(..., description="UUID của khóa học")
    content: str = Field(..., min_length=1, description="Nội dung transcript/tài liệu bài học")


class IndexLessonResponse(CamelModel):
    lesson_id: str
    chunks_indexed: int


class ChurnPredictRequest(CamelModel):
    features: dict[str, float] = Field(
        ...,
        description="{feature_name: value}, snake_case keys as in churn_prediction/predict.py",
    )


class ChurnPredictResponse(CamelModel):
    churn_score: float
    churn_label: bool
    risk_level: str
    model_used: str
    missing_features: list[str]
    warnings: list[str]


class ChurnBatchItem(CamelModel):
    id: str = Field(..., description="Caller's key for this row, echoed back (e.g. enrollment id)")
    features: dict[str, float]


class ChurnBatchRequest(CamelModel):
    items: list[ChurnBatchItem] = Field(..., max_length=1000)


class ChurnBatchResult(CamelModel):
    id: str
    churn_score: float
    churn_label: bool
    risk_level: str
    missing_features: list[str]
    warnings: list[str]


class ChurnBatchResponse(CamelModel):
    model_used: str
    results: list[ChurnBatchResult]


# ---------- Public endpoints (through the API Gateway) ----------

@public.post("/chat", response_model=ApiResponse[ChatResponse], response_model_exclude_none=True)
def chat_endpoint(req: ChatRequest, user: CurrentUser = Depends(current_user)):
    """
    Học viên gửi câu hỏi, nhận câu trả lời + nguồn trích dẫn (chỉ trong phạm vi khóa học
    đã đăng ký). Lịch sử hội thoại được lưu theo user + khóa học.
    """
    course_id = str(req.course_id)
    require_enrollment(user, course_id)
    result = ask_chatbot(
        question=req.question,
        course_id=course_id,
        user_id=user.user_id,
        lesson_id=str(req.lesson_id) if req.lesson_id else None,
    )
    return ok(ChatResponse(**result))


@public.get(
    "/chat/history",
    response_model=ApiResponse[list[ChatHistoryMessage]],
    response_model_exclude_none=True,
)
def chat_history_endpoint(
    course_id: UUID = Query(..., alias="courseId"),
    user: CurrentUser = Depends(current_user),
):
    """Most recent conversation of the current user in the course, oldest message first."""
    rows = get_latest_conversation(user.user_id, str(course_id))
    messages = [
        ChatHistoryMessage(
            role=r["role"],
            content=r["content"],
            sources=r["retrieved_chunks"],
            created_at=r["created_at"],
        )
        for r in rows
    ]
    return ok(messages)


@public.post(
    "/lessons/{lesson_id}/summary",
    response_model=ApiResponse[SummarizeResponse],
    response_model_exclude_none=True,
)
def summarize_lesson_endpoint(lesson_id: UUID, user: CurrentUser = Depends(current_user)):
    """
    Tóm tắt bài học — khác với /chat (chỉ lấy top-K đoạn liên quan tới 1 câu hỏi),
    endpoint này dùng TOÀN BỘ nội dung bài học đã index để tóm tắt bao quát cả bài.
    """
    course_id = get_lesson_course_id(str(lesson_id))
    if course_id is None:
        raise ApiError(404, "LESSON_NOT_INDEXED", "Bài học này chưa được AI xử lý nội dung")
    require_enrollment(user, course_id)
    return ok(SummarizeResponse(**summarize_lesson(lesson_id=str(lesson_id))))


# ---------- Internal endpoints (service-to-service only) ----------

@internal.post(
    "/lessons/index",
    response_model=ApiResponse[IndexLessonResponse],
    response_model_exclude_none=True,
)
def index_lesson_endpoint(req: IndexLessonRequest):
    """
    Indexing — gọi khi có bài học mới publish hoặc nội dung được sửa.
    (Kiến trúc đích: Course Service bắn event LESSON_PUBLISHED qua RabbitMQ,
    AI Service consume event đó rồi gọi index_lesson() thay vì chờ HTTP call.)
    """
    count = index_lesson(lesson_id=str(req.lesson_id), course_id=str(req.course_id), content=req.content)
    return ok(IndexLessonResponse(lesson_id=str(req.lesson_id), chunks_indexed=count))


@internal.post(
    "/churn/predict",
    response_model=ApiResponse[ChurnPredictResponse],
    response_model_exclude_none=True,
)
def predict_churn_endpoint(req: ChurnPredictRequest):
    """
    Dự đoán khả năng học viên bỏ học (churn) bằng model tốt nhất đã train (chọn theo
    AUC-ROC). Trả về churnScore (0-1) và riskLevel (low/medium/high).

    Hiện train trên dữ liệu synthetic — khi có dữ liệu hành vi thật của LearnHub,
    chạy lại `train_model.py`, endpoint này không cần sửa gì thêm.
    """
    try:
        result = predict_churn(req.features)
    except FileNotFoundError as e:
        raise ApiError(503, "MODEL_NOT_TRAINED", str(e))
    return ok(ChurnPredictResponse(**result))


@internal.post(
    "/churn/predict-batch",
    response_model=ApiResponse[ChurnBatchResponse],
    response_model_exclude_none=True,
)
def predict_churn_batch_endpoint(req: ChurnBatchRequest):
    """Batch scoring for the daily churn job in enrollment-service."""
    try:
        predictions = [(item.id, predict_churn(item.features)) for item in req.items]
    except FileNotFoundError as e:
        raise ApiError(503, "MODEL_NOT_TRAINED", str(e))

    results = [
        ChurnBatchResult(
            id=item_id,
            churn_score=p["churn_score"],
            churn_label=p["churn_label"],
            risk_level=p["risk_level"],
            missing_features=p["missing_features"],
            warnings=p["warnings"],
        )
        for item_id, p in predictions
    ]
    model_used = predictions[0][1]["model_used"] if predictions else ""
    return ok(ChurnBatchResponse(model_used=model_used, results=results))


# ---------- Ops ----------

@app.get("/health", tags=["Ops"])
def health_check():
    """Kiểm tra service + kết nối DB còn sống không."""
    try:
        with get_connection() as conn:
            with conn.cursor() as cur:
                cur.execute("SELECT 1")
        return {"status": "ok"}
    except Exception as e:
        raise ApiError(503, "DB_UNAVAILABLE", f"DB connection failed: {e}")


app.include_router(public)
app.include_router(internal)
