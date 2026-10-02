"""
TÓM TẮT BÀI HỌC
================
Khác với RAG Chatbot (chỉ lấy top-K đoạn liên quan nhất tới CÂU HỎI cụ thể),
tóm tắt cần dùng TOÀN BỘ nội dung bài học — không qua bước tìm kiếm similarity,
vì mục đích là bao quát cả bài chứ không phải trả lời 1 câu hỏi cụ thể.

Luồng xử lý:
    lesson_id -> lấy TẤT CẢ chunk của bài học đó từ pgvector (theo đúng thứ tự)
              -> ghép lại thành nội dung đầy đủ
              -> gửi cho LLM, yêu cầu tóm tắt
"""
from typing import Dict

from app.db import get_connection, dict_cursor
from app.llm_client import generate_text

SUMMARIZE_SYSTEM_PROMPT = """Bạn là trợ giảng AI của nền tảng học trực tuyến LearnHub.
Nhiệm vụ của bạn là tóm tắt nội dung bài học được cung cấp, giúp học viên
nắm nhanh ý chính trước khi học chi tiết, hoặc ôn tập lại sau khi đã học xong.

Quy tắc bắt buộc:
1. CHỈ tóm tắt dựa trên nội dung được cung cấp — không thêm kiến thức ngoài phạm vi.
2. Trình bày dạng gạch đầu dòng các ý chính, ngắn gọn, dễ đọc, dễ ôn tập nhanh.
3. Nêu bật khái niệm quan trọng nhất và điểm học viên dễ nhầm lẫn (nếu có).
4. Trả lời bằng cùng ngôn ngữ với nội dung bài học được cung cấp.
"""


def get_lesson_course_id(lesson_id: str) -> str | None:
    """course_id of an indexed lesson, or None if the lesson has not been indexed."""
    with get_connection() as conn:
        cur = dict_cursor(conn)
        cur.execute(
            "SELECT course_id FROM lesson_embeddings WHERE lesson_id = %s LIMIT 1",
            (lesson_id,),
        )
        row = cur.fetchone()
    return str(row["course_id"]) if row else None


def get_full_lesson_content(lesson_id: str) -> str:
    """
    Ghép lại toàn bộ chunk của 1 bài học theo đúng thứ tự (chunk_index) đã
    lưu trong pgvector, để tái tạo lại gần như nguyên văn nội dung gốc.

    Lưu ý: các chunk có phần overlap nhỏ ở đầu/cuối (thiết kế cho Retrieval
    chính xác hơn) — với mục đích tóm tắt, việc lặp một đoạn ngắn giữa 2 chunk
    liền kề không ảnh hưởng đáng kể tới chất lượng tóm tắt.
    """
    with get_connection() as conn:
        cur = dict_cursor(conn)
        cur.execute(
            """
            SELECT chunk_text FROM lesson_embeddings
            WHERE lesson_id = %s
            ORDER BY chunk_index ASC
            """,
            (lesson_id,),
        )
        rows = cur.fetchall()

    return "\n\n".join(row["chunk_text"] for row in rows)


def summarize_lesson(lesson_id: str) -> Dict:
    """
    Tóm tắt 1 bài học dựa trên toàn bộ nội dung đã index.
    Trả về dict {lesson_id, summary}.
    """
    content = get_full_lesson_content(lesson_id)

    if not content:
        return {
            "lesson_id": lesson_id,
            "summary": "Bài học này chưa được index vào hệ thống, không có nội dung để tóm tắt.",
        }

    user_message = f"Nội dung bài học:\n\n{content}\n\nHãy tóm tắt bài học trên."
    summary = generate_text(SUMMARIZE_SYSTEM_PROMPT, user_message)

    return {"lesson_id": lesson_id, "summary": summary}


if __name__ == "__main__":
    # lesson_id của bài "1. Compute / 17. An Overview of AWS Lambda.md" trong
    # khóa "AWS Developer - Associate" — khớp với ID mà index_real_courses.py
    # đã sinh ra khi index bài học này.
    result = summarize_lesson(lesson_id="597fd0bc-91ef-5c01-8018-72f19b0e4a80")
    print("TÓM TẮT:\n", result["summary"])
