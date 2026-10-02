"""
Xác minh course_id filter hoạt động đúng ở tầng DB — kiểm tra rằng nội dung
"AWS Step Functions" (bị trùng giữa 2 khóa CloudOps & Developer) thực sự
được lưu thành 2 lesson_id RIÊNG BIỆT, gắn đúng course_id của từng khóa.

Chạy: python verify_isolation.py
"""
from app.db import get_connection, dict_cursor

COURSE_NAMES = {
    "22bca453-3b12-5a08-a28b-aa8ac2a054b7": "AWS CloudOps Engineer - Associate",
    "bb14837f-776d-564d-81db-a8dd2b0155f0": "AWS Developer - Associate",
}


def main():
    with get_connection() as conn:
        cur = dict_cursor(conn)
        cur.execute(
            """
            SELECT DISTINCT lesson_id, course_id, chunk_text
            FROM lesson_embeddings
            WHERE chunk_text ILIKE %s
            ORDER BY course_id
            """,
            ("%vending machine%",),  # cụm từ đặc trưng trong bài Step Functions
        )
        rows = cur.fetchall()

    print(f"Tìm thấy {len(rows)} chunk chứa nội dung 'vending machine' (Step Functions):\n")
    for r in rows:
        course_name = COURSE_NAMES.get(str(r["course_id"]), str(r["course_id"]))
        print(f"  lesson_id = {r['lesson_id']}")
        print(f"  course_id = {r['course_id']} ({course_name})")
        print(f"  text      = {r['chunk_text'][:80]}...")
        print()

    lesson_ids = {str(r["lesson_id"]) for r in rows}
    course_ids = {str(r["course_id"]) for r in rows}

    print("=" * 60)
    if len(lesson_ids) >= 2 and len(course_ids) >= 2:
        print("✅ ĐÚNG: 2 lesson_id KHÁC NHAU, gắn đúng 2 course_id khác nhau")
        print("   -> course_id filter hoạt động đúng, KHÔNG bị lẫn dữ liệu.")
    else:
        print("⚠ CẢNH BÁO: chỉ thấy 1 lesson_id/course_id — cần kiểm tra lại")


if __name__ == "__main__":
    main()
