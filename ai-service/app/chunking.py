"""
Cắt nhỏ (chunk) transcript/nội dung bài học thành từng đoạn ~500 tokens,
có overlap để không mất ngữ cảnh giữa các đoạn liền kề.

Ví dụ: đoạn A kết thúc ở câu X, đoạn B nên bắt đầu LẶP LẠI một phần
cuối của đoạn A — để nếu câu trả lời nằm vắt ngang 2 đoạn, mình vẫn
tìm thấy đủ ngữ cảnh.
"""
from typing import List
import tiktoken

from app.config import settings

# Dùng bộ encoding chung, đủ tốt để đếm token cho tiếng Việt lẫn tiếng Anh
_encoding = tiktoken.get_encoding("cl100k_base")


def count_tokens(text: str) -> int:
    return len(_encoding.encode(text))


def chunk_text(
    text: str,
    chunk_size: int = None,
    overlap: int = None,
) -> List[str]:
    """
    Cắt text thành list các chunk theo số lượng token.

    Args:
        text: nội dung transcript/tài liệu bài học (đã làm sạch)
        chunk_size: số token tối đa mỗi chunk (mặc định lấy từ config)
        overlap: số token lặp lại giữa 2 chunk liền kề

    Returns:
        List[str]: danh sách các đoạn text đã cắt
    """
    chunk_size = chunk_size or settings.CHUNK_SIZE_TOKENS
    overlap = overlap or settings.CHUNK_OVERLAP_TOKENS

    tokens = _encoding.encode(text)
    if len(tokens) <= chunk_size:
        return [text]

    chunks = []
    start = 0
    while start < len(tokens):
        end = min(start + chunk_size, len(tokens))
        chunk_tokens = tokens[start:end]
        chunks.append(_encoding.decode(chunk_tokens))

        if end == len(tokens):
            break
        start = end - overlap  # lùi lại `overlap` token để tạo phần giao nhau

    return chunks


if __name__ == "__main__":
    # Test nhanh
    sample = "Đây là câu test. " * 300
    result = chunk_text(sample, chunk_size=100, overlap=20)
    print(f"Số chunk: {len(result)}")
    for i, c in enumerate(result):
        print(f"Chunk {i}: {count_tokens(c)} tokens")
