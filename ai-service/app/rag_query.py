"""
QUERY PIPELINE (phần "R" và "G" trong RAG)
============================================
Chạy mỗi khi học viên đặt câu hỏi trong lúc học.

Luồng xử lý:
    Câu hỏi của học viên
        -> embed_text()                          [embeddings.py]
        -> query pgvector, tìm top-K chunk gần nhất (Retrieval)
        -> ghép context + câu hỏi -> gọi LLM      [llm_client.py]  (Generation)
        -> trả về câu trả lời + nguồn đã dùng
"""
from typing import List, Dict

from app.config import settings
from app.db import get_connection, dict_cursor
from app.embeddings import embed_text
from app.chat_history import get_or_create_session, save_message
from app.llm_client import generate_text

SYSTEM_PROMPT = """Bạn là trợ giảng AI của nền tảng học trực tuyến LearnHub.
Nhiệm vụ của bạn là trả lời câu hỏi của học viên CHỈ dựa trên nội dung
bài học được cung cấp trong phần "Ngữ cảnh" dưới đây.

QUY TẮC QUAN TRỌNG NHẤT — NGÔN NGỮ TRẢ LỜI (luôn ưu tiên tuân thủ):
Luôn trả lời bằng ĐÚNG ngôn ngữ của CÂU HỎI, bất kể "Ngữ cảnh" được cung cấp
bằng ngôn ngữ nào (VD: ngữ cảnh tiếng Anh nhưng câu hỏi tiếng Việt -> vẫn phải
trả lời bằng tiếng Việt, tự dịch/diễn giải nội dung sang đúng ngôn ngữ đó).

Ví dụ minh họa:
- Câu hỏi: "What is AWS Lambda?"
  -> Trả lời: "AWS Lambda is a serverless compute service that lets you run code..."
- Câu hỏi: "AWS Lambda là gì?"
  -> Trả lời: "AWS Lambda là một dịch vụ điện toán serverless cho phép bạn chạy mã..."

Các quy tắc khác:
1. CHỈ trả lời dựa trên ngữ cảnh được cung cấp — không dùng kiến thức
   ngoài phạm vi đó, không bịa thông tin.
2. Nếu ngữ cảnh không đủ để trả lời, hãy nói rõ (bằng đúng ngôn ngữ câu hỏi):
   - Tiếng Việt: "Nội dung bài học hiện tại chưa đề cập đến vấn đề này."
   - Tiếng Anh: "The lesson content does not cover this topic."
3. Trả lời ngắn gọn, dễ hiểu, có thể trích dẫn nguyên văn phần liên quan
   nếu cần thiết.
4. Đoạn có nhãn "bài học đang mở" thuộc bài học viên đang xem — khi học viên
   nói "bài này"/"this lesson" thì hiểu là bài đó. Các đoạn khác thuộc bài khác
   trong cùng khóa học, vẫn được dùng để trả lời.
"""


# Chunks of the lesson the student has open, always added so "this lesson" questions have context
CURRENT_LESSON_CHUNKS = 2


def retrieve_chunks(
    question: str, course_id: str, top_k: int = None, lesson_id: str = None
) -> List[Dict]:
    """
    Bước Retrieval: embed câu hỏi, tìm top-K chunk có nghĩa gần nhất
    trong phạm vi 1 khóa học cụ thể.

    pgvector dùng operator `<=>` để tính cosine distance
    (giá trị càng nhỏ = càng giống nhau).
    """
    top_k = top_k or settings.TOP_K_RESULTS
    # task_type="RETRIEVAL_QUERY" vì đây là embedding cho CÂU HỎI, khác với
    # lúc index nội dung bài học (task_type="RETRIEVAL_DOCUMENT" — mặc định trong embed_text)
    question_vector = embed_text(question, task_type="RETRIEVAL_QUERY")

    with get_connection() as conn:
        cur = dict_cursor(conn)
        cur.execute(
            """
            SELECT
                lesson_id,
                chunk_index,
                chunk_text,
                1 - (embedding <=> %s::vector) AS similarity
            FROM lesson_embeddings
            WHERE course_id = %s
            ORDER BY embedding <=> %s::vector
            LIMIT %s
            """,
            (question_vector, course_id, question_vector, top_k),
        )
        rows = [dict(row) for row in cur.fetchall()]

        current = []
        if lesson_id:
            cur.execute(
                """
                SELECT
                    lesson_id,
                    chunk_index,
                    chunk_text,
                    1 - (embedding <=> %s::vector) AS similarity
                FROM lesson_embeddings
                WHERE course_id = %s AND lesson_id = %s
                ORDER BY embedding <=> %s::vector
                LIMIT %s
                """,
                (question_vector, course_id, lesson_id, question_vector, CURRENT_LESSON_CHUNKS),
            )
            current = [dict(row) for row in cur.fetchall()]

    seen = {(str(c["lesson_id"]), c["chunk_index"]) for c in current}
    chunks = current + [r for r in rows if (str(r["lesson_id"]), r["chunk_index"]) not in seen]
    for c in chunks:
        c["is_current_lesson"] = lesson_id is not None and str(c["lesson_id"]) == lesson_id
    return chunks


def generate_answer(question: str, chunks: List[Dict]) -> str:
    """
    Bước Generation: ghép các chunk tìm được thành context,
    gửi cho LLM (Claude hoặc Gemini, tùy settings.LLM_PROVIDER) cùng câu hỏi,
    yêu cầu trả lời trong phạm vi context đó.
    """
    if not chunks:
        return "Nội dung bài học hiện tại chưa đề cập đến vấn đề này."

    context = "\n\n---\n\n".join(
        f"[Đoạn {i+1}{' — bài học đang mở' if c.get('is_current_lesson') else ''}]\n{c['chunk_text']}"
        for i, c in enumerate(chunks)
    )

    user_message = f"""Ngữ cảnh (trích từ nội dung bài học):
{context}

Câu hỏi của học viên: {question}

(Nhắc lại: trả lời bằng ĐÚNG ngôn ngữ của câu hỏi ở trên — không dùng ngôn ngữ khác.)"""

    return generate_text(SYSTEM_PROMPT, user_message)


def ask_chatbot(
    question: str, course_id: str, user_id: str = None, lesson_id: str = None
) -> Dict:
    """
    Hàm tổng hợp toàn bộ pipeline: Retrieval + Generation.
    Đây là hàm mà API endpoint /chat sẽ gọi.

    Nếu có user_id, tự động lưu lại lịch sử hội thoại (câu hỏi + câu trả lời)
    vào chatbot_sessions/chatbot_messages — phục vụ demo "chatbot có nhớ lịch sử"
    và phân tích sau này. Không có user_id (VD: khi test bằng script) thì bỏ qua
    bước lưu, không lỗi gì cả.
    """
    chunks = retrieve_chunks(question, course_id, lesson_id=lesson_id)
    answer = generate_answer(question, chunks)

    sources = [
        {
            "lesson_id": c["lesson_id"],
            "chunk_index": c["chunk_index"],
            "text_preview": c["chunk_text"][:150] + "...",
            "similarity": round(c["similarity"], 4),
        }
        for c in chunks
    ]

    if user_id:
        session_id = get_or_create_session(user_id, course_id)
        save_message(session_id, role="user", content=question)
        save_message(
            session_id,
            role="assistant",
            content=answer,
            retrieved_chunks=sources,
            model_used=settings.GEMINI_MODEL if settings.LLM_PROVIDER == "gemini" else settings.CHAT_MODEL,
        )

    return {"answer": answer, "sources": sources}


if __name__ == "__main__":
    # course_id của khóa "AWS Developer - Associate" — sinh cố định bằng uuid5,
    # khớp với ID mà index_real_courses.py đã dùng khi index khóa học này.
    # user_id demo cố định -> mỗi lần chạy sẽ nối tiếp vào cùng 1 session,
    # dùng để test luôn phần lưu lịch sử chat (chatbot_sessions/chatbot_messages)
    result = ask_chatbot(
        question="What is AWS Lambda and how is it invoked?",
        course_id="bb14837f-776d-564d-81db-a8dd2b0155f0",
        user_id="88888888-8888-8888-8888-888888888888",
    )
    print("TRẢ LỜI:", result["answer"])
    print("\nNGUỒN:")
    for s in result["sources"]:
        print(f"  - similarity={s['similarity']}: {s['text_preview']}")
