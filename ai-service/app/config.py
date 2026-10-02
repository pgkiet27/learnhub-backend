"""
Đọc toàn bộ config từ file .env — mọi module khác import từ đây,
không đọc os.environ rải rác khắp nơi.
"""
import os
import sys
from dotenv import load_dotenv

load_dotenv()

# Windows console mặc định dùng cp1252, không in được tiếng Việt (UnicodeEncodeError).
# Ép stdout/stderr sang UTF-8 ngay khi import config, để mọi script print() tiếng Việt
# đều chạy được mà không cần set PYTHONUTF8=1 thủ công mỗi lần.
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")


class Settings:
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL", "postgresql://ai_user:ai_password@localhost:5438/ai_db"
    )

    # Enrollment check (called directly, not through the gateway)
    ENROLLMENT_SERVICE_URL: str = os.getenv("ENROLLMENT_SERVICE_URL", "http://localhost:8084")
    ENROLLMENT_CHECK_ENABLED: bool = os.getenv("ENROLLMENT_CHECK_ENABLED", "true").lower() == "true"

    ANTHROPIC_API_KEY: str = os.getenv("ANTHROPIC_API_KEY", "")
    GEMINI_API_KEY: str = os.getenv("GEMINI_API_KEY", "")

    # "gemini" (free tier, dùng để dev/test) hoặc "claude" (dùng cho bản chính thức nộp báo cáo)
    # Đổi provider chỉ cần sửa LLM_PROVIDER trong .env, KHÔNG cần sửa code.
    LLM_PROVIDER: str = os.getenv("LLM_PROVIDER", "gemini")

    # Embedding luôn dùng Gemini (free tier) — không phụ thuộc LLM_PROVIDER,
    # vì OpenAI Embedding tốn phí ngay từ request đầu tiên, không có gói miễn phí.
    EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "gemini-embedding-001")
    CHAT_MODEL: str = os.getenv("CHAT_MODEL", "claude-sonnet-4-5")
    GEMINI_MODEL: str = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

    CHUNK_SIZE_TOKENS: int = int(os.getenv("CHUNK_SIZE_TOKENS", 500))
    CHUNK_OVERLAP_TOKENS: int = int(os.getenv("CHUNK_OVERLAP_TOKENS", 50))
    TOP_K_RESULTS: int = int(os.getenv("TOP_K_RESULTS", 3))


settings = Settings()
