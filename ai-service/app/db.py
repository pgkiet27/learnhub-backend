"""
Kết nối PostgreSQL + pgvector.
Dùng psycopg2 thuần (không ORM) để dễ kiểm soát câu lệnh SQL,
phù hợp cho 1 service nhỏ như AI Service.
"""
import psycopg2
import psycopg2.extras
from pgvector.psycopg2 import register_vector
from contextlib import contextmanager

from app.config import settings


@contextmanager
def get_connection():
    """Context manager mở/đóng connection tự động, tránh leak connection."""
    conn = psycopg2.connect(settings.DATABASE_URL)
    register_vector(conn)  # cho phép psycopg2 hiểu kiểu dữ liệu `vector`
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def dict_cursor(conn):
    """Trả cursor mà mỗi row là dict thay vì tuple, code dễ đọc hơn."""
    return conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor)
