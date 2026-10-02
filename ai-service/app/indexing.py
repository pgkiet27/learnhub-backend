"""
INDEXING PIPELINE
==================
Chạy khi có bài học mới được publish (trong thực tế: consume event
"LESSON_PUBLISHED" từ RabbitMQ do Course Service bắn ra).

Ở bản demo/MVP này, ta gọi trực tiếp hàm index_lesson() bằng tay
hoặc qua API — sau này chỉ cần thay chỗ gọi bằng RabbitMQ consumer,
logic bên trong giữ nguyên.

Luồng xử lý:
    Nội dung bài học (transcript/text)
        -> chunk_text()          [chunking.py]
        -> embed_batch()         [embeddings.py]
        -> lưu vào bảng lesson_embeddings
"""
import uuid
from typing import Optional

from app.db import get_connection
from app.chunking import chunk_text, count_tokens
from app.embeddings import embed_batch


def is_lesson_indexed(lesson_id: str) -> bool:
    """
    Kiểm tra 1 bài học đã được index vào pgvector chưa — dùng để SKIP
    khi chạy lại script batch (VD: index_real_courses.py) sau khi bị dừng
    giữa chừng vì hết quota, tránh lãng phí quota gọi lại Embedding API
    cho những bài đã index thành công rồi.
    """
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT 1 FROM lesson_embeddings WHERE lesson_id = %s LIMIT 1",
                (lesson_id,),
            )
            return cur.fetchone() is not None


def index_lesson(
    lesson_id: str,
    course_id: str,
    content: str,
) -> int:
    """
    Index (đánh chỉ mục) nội dung 1 bài học vào Vector DB.

    Args:
        lesson_id: UUID của bài học
        course_id: UUID của khóa học
        content: toàn bộ transcript/nội dung text của bài học

    Returns:
        int: số chunk đã được lưu
    """
    chunks = chunk_text(content)
    if not chunks:
        return 0

    print(f"[Indexing] Lesson {lesson_id}: chia thành {len(chunks)} chunk")

    vectors = embed_batch(chunks)

    with get_connection() as conn:
        with conn.cursor() as cur:
            # Xóa embedding cũ của bài học này (nếu re-index do nội dung được sửa)
            cur.execute(
                "DELETE FROM lesson_embeddings WHERE lesson_id = %s",
                (lesson_id,),
            )

            for idx, (chunk, vector) in enumerate(zip(chunks, vectors)):
                cur.execute(
                    """
                    INSERT INTO lesson_embeddings
                        (id, lesson_id, course_id, chunk_index, chunk_text, embedding, token_count)
                    VALUES (%s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        str(uuid.uuid4()),
                        lesson_id,
                        course_id,
                        idx,
                        chunk,
                        vector,
                        count_tokens(chunk),
                    ),
                )

    print(f"[Indexing] Hoàn tất: đã lưu {len(chunks)} chunk vào pgvector")
    return len(chunks)


if __name__ == "__main__":
    # Test nhanh: index thử 1 bài học thật từ khóa "AWS Developer - Associate"
    import pathlib

    sample_path = (
        pathlib.Path(r"C:\Users\ACER\Desktop\KLTN\AWS Developer - Associate")
        / "English" / "1. Compute" / "17. An Overview of AWS Lambda.md"
    )
    content = sample_path.read_text(encoding="utf-8")

    index_lesson(
        lesson_id="597fd0bc-91ef-5c01-8018-72f19b0e4a80",
        course_id="bb14837f-776d-564d-81db-a8dd2b0155f0",
        content=content,
    )
