"""
Lưu và truy vấn lịch sử chat — dùng bảng chatbot_sessions/chatbot_messages
đã định nghĩa sẵn trong schema.sql.

Mục đích: phục vụ demo/báo cáo (chứng minh chatbot có "nhớ" lịch sử),
và sau này có thể dùng để phân tích học viên hay hỏi gì (feed vào AI Service
khác, ví dụ FAQ tự động).
"""
import json
import uuid
from typing import List, Dict, Optional

from app.db import get_connection, dict_cursor


def get_or_create_session(user_id: str, course_id: str) -> str:
    """
    Tìm session gần nhất của user trong khóa học này để tiếp tục hội thoại;
    nếu chưa có, tạo session mới. Trả về session_id.
    """
    with get_connection() as conn:
        cur = dict_cursor(conn)
        cur.execute(
            """
            SELECT id FROM chatbot_sessions
            WHERE user_id = %s AND course_id = %s
            ORDER BY last_message_at DESC
            LIMIT 1
            """,
            (user_id, course_id),
        )
        row = cur.fetchone()
        if row:
            return str(row["id"])

        session_id = str(uuid.uuid4())
        cur.execute(
            "INSERT INTO chatbot_sessions (id, user_id, course_id) VALUES (%s, %s, %s)",
            (session_id, user_id, course_id),
        )
        return session_id


def save_message(
    session_id: str,
    role: str,
    content: str,
    retrieved_chunks: Optional[List[Dict]] = None,
    model_used: Optional[str] = None,
) -> None:
    """Lưu 1 tin nhắn (role: 'user' hoặc 'assistant') vào chatbot_messages."""
    with get_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            """
            INSERT INTO chatbot_messages (id, session_id, role, content, retrieved_chunks, model_used)
            VALUES (%s, %s, %s, %s, %s, %s)
            """,
            (
                str(uuid.uuid4()),
                session_id,
                role,
                content,
                json.dumps(retrieved_chunks) if retrieved_chunks else None,
                model_used,
            ),
        )
        cur.execute(
            "UPDATE chatbot_sessions SET last_message_at = NOW() WHERE id = %s",
            (session_id,),
        )


def get_latest_conversation(user_id: str, course_id: str, limit: int = 50) -> List[Dict]:
    """Messages of the user's most recent session in the course, oldest first (empty if none)."""
    with get_connection() as conn:
        cur = dict_cursor(conn)
        cur.execute(
            """
            SELECT role, content, retrieved_chunks, created_at
            FROM (
                SELECT m.role, m.content, m.retrieved_chunks, m.created_at
                FROM chatbot_messages m
                WHERE m.session_id = (
                    SELECT id FROM chatbot_sessions
                    WHERE user_id = %s AND course_id = %s
                    ORDER BY last_message_at DESC
                    LIMIT 1
                )
                ORDER BY m.created_at DESC
                LIMIT %s
            ) recent
            ORDER BY created_at ASC
            """,
            (user_id, course_id, limit),
        )
        return [dict(row) for row in cur.fetchall()]


def get_session_history(session_id: str) -> List[Dict]:
    """Lấy toàn bộ lịch sử tin nhắn của 1 session, theo thứ tự thời gian — dùng để demo/debug."""
    with get_connection() as conn:
        cur = dict_cursor(conn)
        cur.execute(
            """
            SELECT role, content, model_used, created_at
            FROM chatbot_messages
            WHERE session_id = %s
            ORDER BY created_at ASC
            """,
            (session_id,),
        )
        return [dict(row) for row in cur.fetchall()]
