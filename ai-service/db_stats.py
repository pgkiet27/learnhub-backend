"""
Xem thống kê dữ liệu đã index trong pgvector.
Chạy: python db_stats.py
"""
from app.db import get_connection, dict_cursor


def main():
    with get_connection() as conn:
        cur = dict_cursor(conn)

        cur.execute("SELECT COUNT(DISTINCT lesson_id) AS lessons, COUNT(*) AS chunks FROM lesson_embeddings")
        row = cur.fetchone()
        print(f"Tổng số bài học đã index : {row['lessons']}")
        print(f"Tổng số chunk            : {row['chunks']}")

        print("\nChi tiết theo khóa học:")
        cur.execute(
            """
            SELECT course_id,
                   COUNT(DISTINCT lesson_id) AS lessons,
                   COUNT(*) AS chunks
            FROM lesson_embeddings
            GROUP BY course_id
            ORDER BY course_id
            """
        )
        course_names = {
            "22bca453-3b12-5a08-a28b-aa8ac2a054b7": "AWS CloudOps Engineer - Associate",
            "bb14837f-776d-564d-81db-a8dd2b0155f0": "AWS Developer - Associate",
        }
        for r in cur.fetchall():
            name = course_names.get(str(r["course_id"]), str(r["course_id"]))
            print(f"  - {name}: {r['lessons']} bài, {r['chunks']} chunk")

        cur.execute("SELECT COUNT(*) AS sessions FROM chatbot_sessions")
        s = cur.fetchone()
        cur.execute("SELECT COUNT(*) AS messages FROM chatbot_messages")
        m = cur.fetchone()
        print(f"\nLịch sử chat: {s['sessions']} session, {m['messages']} tin nhắn")


if __name__ == "__main__":
    main()
