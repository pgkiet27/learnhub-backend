"""
Gọi Gemini Embedding API để biến 1 đoạn text thành vector số (3072 chiều,
mặc định của model gemini-embedding-001).
Vector này là "dấu vân tay ngữ nghĩa" của đoạn text — 2 đoạn text có nghĩa
gần nhau sẽ có vector gần nhau trong không gian nhiều chiều.

Dùng free tier của Google (không tốn phí như OpenAI Embedding).

Lưu ý: dùng SDK mới `google-genai` (package "google-generativeai" cũ đã
bị Google deprecated, không còn được cập nhật/hỗ trợ model mới).
"""
from typing import List
from google import genai
from google.genai import types
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type
from google.genai.errors import ServerError

from app.config import settings

_client = genai.Client(api_key=settings.GEMINI_API_KEY)

# Dùng chung cho embed_text/embed_batch — khi index hàng trăm bài liên tiếp
# (VD: index_real_courses.py), rất dễ gặp lỗi 503 tạm thời từ phía Google,
# nên cần retry thay vì để cả script dừng giữa chừng.
_retry_on_server_error = retry(
    retry=retry_if_exception_type(ServerError),
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10),
    reraise=True,
)


@_retry_on_server_error
def embed_text(text: str, task_type: str = "RETRIEVAL_DOCUMENT") -> List[float]:
    """
    Embed 1 đoạn text -> trả về vector (list[float]).

    task_type:
        "RETRIEVAL_DOCUMENT" — dùng khi embed NỘI DUNG bài học để lưu vào DB (Indexing)
        "RETRIEVAL_QUERY"    — dùng khi embed CÂU HỎI của học viên (Query)
        Gemini tối ưu embedding khác nhau tùy mục đích, nên cần khai báo đúng.
    """
    response = _client.models.embed_content(
        model=settings.EMBEDDING_MODEL,
        contents=text,
        config=types.EmbedContentConfig(task_type=task_type),
    )
    return response.embeddings[0].values


@_retry_on_server_error
def embed_batch(texts: List[str], task_type: str = "RETRIEVAL_DOCUMENT") -> List[List[float]]:
    """
    Embed nhiều đoạn text cùng lúc — nhanh hơn gọi API từng cái một.
    Trả về list vector đúng theo thứ tự input.
    """
    response = _client.models.embed_content(
        model=settings.EMBEDDING_MODEL,
        contents=texts,
        config=types.EmbedContentConfig(task_type=task_type),
    )
    return [e.values for e in response.embeddings]


if __name__ == "__main__":
    vec = embed_text("React Hooks dùng để quản lý state trong function component.")
    print(f"Độ dài vector: {len(vec)}")
    print(f"5 giá trị đầu: {vec[:5]}")
