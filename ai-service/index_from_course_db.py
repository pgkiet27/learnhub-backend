"""
Index published lessons from course_db under their real IDs. Safe to re-run: skips indexed lessons,
waits out per-minute rate limits, stops when the daily Gemini quota runs out.

Run: python index_from_course_db.py [--course <course_id>] [--limit N]
"""
import argparse
import os
import time

import psycopg2
import psycopg2.extras
from google.genai.errors import ClientError

from app.indexing import index_lesson, is_lesson_indexed

COURSE_DB_URL = os.getenv(
    "COURSE_DB_URL", "postgresql://learnhub:learnhub123@localhost:5435/course_db"
)
DELAY_BETWEEN_LESSONS_SEC = 1.5
MIN_CONTENT_CHARS = 50
RATE_LIMIT_WAIT_SEC = 65
MAX_RATE_LIMIT_RETRIES = 3  # still 429 after this many waits => daily quota


def fetch_lessons(course_id: str | None) -> list[dict]:
    sql = """
        SELECT l.id::text AS lesson_id, l.course_id::text AS course_id,
               c.title AS course_title, s.title AS section_title, l.title,
               COALESCE(NULLIF(TRIM(l.transcript), ''), NULLIF(TRIM(l.content), '')) AS body
        FROM lessons l
        JOIN sections s ON s.id = l.section_id
        JOIN courses c ON c.id = l.course_id
        WHERE l.is_published
          AND c.status = 'published'
          AND s.title NOT LIKE 'Practice Labs%%'  -- lab guides are not Q&A material
          AND (%(course_id)s::uuid IS NULL OR l.course_id = %(course_id)s::uuid)
        ORDER BY c.title, s.display_order, l.display_order
    """
    with psycopg2.connect(COURSE_DB_URL) as conn:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, {"course_id": course_id})
            return [dict(r) for r in cur.fetchall()]


def main():
    parser = argparse.ArgumentParser(description="Index published lessons from course_db")
    parser.add_argument("--course", help="only this course_id")
    parser.add_argument("--limit", type=int, help="index at most N new lessons in this run")
    args = parser.parse_args()

    lessons = fetch_lessons(args.course)
    print(f"{len(lessons)} published lessons found in course_db")

    indexed = skipped = too_short = chunks = 0
    for lesson in lessons:
        if args.limit is not None and indexed >= args.limit:
            break
        if is_lesson_indexed(lesson["lesson_id"]):
            skipped += 1
            continue
        body = lesson["body"] or ""
        if len(body) < MIN_CONTENT_CHARS:
            too_short += 1
            continue

        label = f"{lesson['course_title']} / {lesson['section_title']} / {lesson['title']}"
        quota_exhausted = False
        for attempt in range(MAX_RATE_LIMIT_RETRIES + 1):
            try:
                chunks += index_lesson(
                    lesson_id=lesson["lesson_id"],
                    course_id=lesson["course_id"],
                    content=f"{lesson['title']}\n\n{body}",
                )
                indexed += 1
                print(f"  [OK] {label}", flush=True)
                break
            except ClientError as e:
                if e.code != 429:
                    print(f"  [ERROR] {label}: {e}")
                    break
                if attempt == MAX_RATE_LIMIT_RETRIES:
                    quota_exhausted = True
                    break
                print(f"  rate limited, waiting {RATE_LIMIT_WAIT_SEC}s...", flush=True)
                time.sleep(RATE_LIMIT_WAIT_SEC)
        if quota_exhausted:
            print(f"\nGemini quota exhausted at: {label} — run again after the quota resets.")
            break
        time.sleep(DELAY_BETWEEN_LESSONS_SEC)

    print(
        f"\nIndexed {indexed} lessons ({chunks} chunks), "
        f"{skipped} already indexed, {too_short} without enough content."
    )


if __name__ == "__main__":
    main()
