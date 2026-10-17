"""
LLM Client dùng chung cho toàn bộ AI Service — cả RAG Chatbot (rag_query.py)
lẫn Tóm tắt bài học (summarize.py) đều gọi qua đây, tránh khởi tạo client
trùng lặp ở nhiều nơi.

Tự động chọn Claude hoặc Gemini theo settings.LLM_PROVIDER — nơi gọi hàm
generate_text() không cần biết đang dùng provider nào.
"""
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type
from google.genai.errors import ServerError  # dùng để retry khi Gemini quá tải (503)

from app.config import settings

# Chỉ khởi tạo client của provider đang dùng, tránh lỗi thiếu API key
# của provider không dùng tới.
if settings.LLM_PROVIDER == "claude":
    from anthropic import Anthropic

    _claude = Anthropic(api_key=settings.ANTHROPIC_API_KEY)
elif settings.LLM_PROVIDER == "gemini":
    from google import genai
    from google.genai import types as genai_types

    _gemini = genai.Client(api_key=settings.GEMINI_API_KEY)
else:
    raise ValueError(f"LLM_PROVIDER không hợp lệ: {settings.LLM_PROVIDER!r} (chỉ chấp nhận 'claude' hoặc 'gemini')")


def _generate_with_claude(system_prompt: str, user_message: str) -> str:
    response = _claude.messages.create(
        model=settings.CHAT_MODEL,
        max_tokens=1024,
        system=system_prompt,
        messages=[{"role": "user", "content": user_message}],
    )
    return response.content[0].text


@retry(
    # Chỉ retry khi Google trả lỗi server tạm thời (503 quá tải) — KHÔNG retry
    # các lỗi khác (VD: sai API key, nội dung bị chặn) vì retry sẽ vô ích.
    retry=retry_if_exception_type(ServerError),
    stop=stop_after_attempt(3),
    wait=wait_exponential(multiplier=1, min=2, max=10),
    reraise=True,
)
def _generate_with_gemini(system_prompt: str, user_message: str) -> str:
    response = _gemini.models.generate_content(
        model=settings.GEMINI_MODEL,
        contents=user_message,
        config=genai_types.GenerateContentConfig(
            system_instruction=system_prompt,
            # temperature thấp -> model bám sát instruction hơn (ít "sáng tạo" lệch
            # hướng), giúp tuân thủ quy tắc ngôn ngữ trong system prompt ổn định hơn.
            temperature=0.2,
        ),
    )
    return response.text


def generate_text(system_prompt: str, user_message: str) -> str:
    """
    Hàm dùng chung: gửi (system_prompt + user_message) cho LLM đang cấu hình
    (Claude hoặc Gemini, tùy settings.LLM_PROVIDER) và trả về text trả lời.
    """
    if settings.LLM_PROVIDER == "claude":
        return _generate_with_claude(system_prompt, user_message)
    else:
        return _generate_with_gemini(system_prompt, user_message)
