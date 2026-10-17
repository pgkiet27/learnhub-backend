"""
Kiểm tra nhanh còn quota Embedding của Gemini free tier hay không.
Gọi đúng 1 request nhỏ nhất (1 từ) rồi báo kết quả.

Chạy: python check_quota.py
"""
from google.genai.errors import ClientError
from app.embeddings import embed_text


def main():
    try:
        vec = embed_text("test", task_type="RETRIEVAL_QUERY")
        print("✅ CÒN QUOTA — Embedding API gọi được bình thường.")
        print(f"   (vector trả về {len(vec)} chiều)")
        print("\n   -> Có thể chạy: python index_real_courses.py  /  python -m app.rag_query")
    except ClientError as e:
        if "RESOURCE_EXHAUSTED" in str(e):
            print("❌ HẾT QUOTA — quota Embedding free tier trong ngày đã dùng hết.")
            print("   Đợi reset (thường nửa đêm giờ Thái Bình Dương ≈ 14-15h chiều giờ VN),")
            print("   hoặc đổi sang GEMINI_API_KEY khác trong .env.")
        else:
            print(f"⚠ Lỗi khác (không phải hết quota): {e}")
    except Exception as e:
        print(f"⚠ Lỗi không xác định: {e}")


if __name__ == "__main__":
    main()
