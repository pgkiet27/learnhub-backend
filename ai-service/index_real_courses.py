"""
Index nội dung 2 khóa học AWS THẬT (do bạn cung cấp) vào Vector DB.

Chỉ index phần "English" (bài giảng lý thuyết dạng transcript) — CHỦ Ý
BỎ QUA "Practice Labs" (hướng dẫn thực hành từng bước, chứa credential lab
tạm thời và ảnh chụp màn hình, không phù hợp làm nguồn Q&A cho RAG Chatbot).

Cấu trúc dữ liệu nguồn:
    <Course Name>/English/<Section Name>/<N>. <Lesson Title>.md
    -> Course Name  = tên khóa học (VD: "AWS Developer - Associate")
    -> Section Name = tên chương/section (VD: "1. Compute")
    -> Mỗi file .md = 1 bài học (dòng đầu là URL video, sau đó là transcript)

course_id/lesson_id được sinh CỐ ĐỊNH (uuid5) từ tên/đường dẫn — nghĩa là
chạy lại script này nhiều lần vẫn ra đúng ID cũ (an toàn để resume nếu
bị dừng giữa chừng, không tạo dữ liệu trùng lặp).

TỰ ĐỘNG BỎ QUA bài đã index rồi (kiểm tra qua is_lesson_indexed()) — an toàn
để chạy lại nhiều lần mà không lãng phí quota Embedding API cho việc làm lại
những bài đã xong.

TỰ ĐỘNG DỪNG nếu phát hiện hết quota miễn phí trong ngày (lỗi 429
RESOURCE_EXHAUSTED) — thay vì cố chạy tiếp và để hết các bài còn lại đều lỗi
liên tục (gây rối terminal). Chạy lại script sau khi quota reset để tiếp tục,
các bài đã xong sẽ tự động được bỏ qua.

Chạy: python index_real_courses.py
"""
import time
import uuid
import pathlib

from google.genai.errors import ClientError

from app.indexing import index_lesson, is_lesson_indexed

# Namespace cố định để uuid5 luôn sinh ra ID giống nhau cho cùng 1 input
NAMESPACE = uuid.UUID("12345678-1234-5678-1234-567812345678")

BASE_DIR = pathlib.Path(r"C:\Users\ACER\Desktop\KLTN")
COURSES = {
    "AWS CloudOps Engineer - Associate": BASE_DIR / "AWS CloudOps Engineer - Associate" / "English",
    "AWS Developer - Associate": BASE_DIR / "AWS Developer - Associate" / "English",
}

DELAY_BETWEEN_LESSONS_SEC = 1.5  # tránh bị rate limit khi gọi Embedding API liên tục


def clean_content(raw_text: str) -> str:
    """Bỏ dòng 'Video: https://...' ở đầu file — chỉ là URL tạm thời (có chữ ký hết hạn), không phải nội dung học."""
    lines = raw_text.splitlines()
    if lines and lines[0].strip().startswith("Video:"):
        lines = lines[1:]
    return "\n".join(lines).strip()


def is_quota_exhausted_error(e: Exception) -> bool:
    return isinstance(e, ClientError) and "RESOURCE_EXHAUSTED" in str(e)


def main():
    total_lessons = 0
    total_skipped_already_indexed = 0
    total_chunks = 0
    skipped_short = []
    failed = []
    quota_exhausted = False

    for course_name, course_path in COURSES.items():
        if quota_exhausted:
            break
        if not course_path.exists():
            print(f"⚠ Không tìm thấy folder: {course_path}")
            continue

        course_id = str(uuid.uuid5(NAMESPACE, course_name))
        print(f"\n{'=' * 70}")
        print(f"KHÓA HỌC: {course_name}")
        print(f"course_id = {course_id}")
        print(f"{'=' * 70}")

        # Loại "Practice Labs" ra khỏi danh sách section cần index
        section_dirs = sorted(
            d for d in course_path.iterdir()
            if d.is_dir() and d.name != "Practice Labs"
        )

        for section_dir in section_dirs:
            if quota_exhausted:
                break
            md_files = sorted(section_dir.glob("*.md"))
            print(f"\n--- Section: {section_dir.name} ({len(md_files)} bài) ---")

            for md_file in md_files:
                relative_key = f"{course_name}/{section_dir.name}/{md_file.name}"
                lesson_id = str(uuid.uuid5(NAMESPACE, relative_key))

                if is_lesson_indexed(lesson_id):
                    total_skipped_already_indexed += 1
                    continue

                try:
                    raw = md_file.read_text(encoding="utf-8")
                    content = clean_content(raw)

                    if len(content) < 50:
                        print(f"  [BỎ QUA] {md_file.name} — nội dung quá ngắn sau khi làm sạch")
                        skipped_short.append(relative_key)
                        continue

                    chunks = index_lesson(lesson_id=lesson_id, course_id=course_id, content=content)
                    total_lessons += 1
                    total_chunks += chunks

                except Exception as e:
                    if is_quota_exhausted_error(e):
                        print(f"\n⚠ HẾT QUOTA MIỄN PHÍ TRONG NGÀY (Gemini Embedding) — dừng tại: {relative_key}")
                        print(f"  Chạy lại script này sau khi quota reset để tiếp tục —")
                        print(f"  các bài đã index rồi sẽ tự động được bỏ qua, không lãng phí quota.")
                        failed.append(relative_key)
                        quota_exhausted = True
                        break
                    print(f"  [LỖI] {md_file.name}: {e}")
                    failed.append(relative_key)

                time.sleep(DELAY_BETWEEN_LESSONS_SEC)

    print(f"\n{'=' * 70}")
    print(f"TỔNG KẾT LẦN CHẠY NÀY")
    print(f"  - Mới index thành công: {total_lessons} bài học, {total_chunks} chunk")
    print(f"  - Đã index từ trước (bỏ qua): {total_skipped_already_indexed} bài")
    print(f"  - Bỏ qua (nội dung quá ngắn): {len(skipped_short)} bài")
    print(f"  - Lỗi: {len(failed)} bài")
    if quota_exhausted:
        print(f"\n  ⚠ Dừng sớm vì hết quota — vẫn còn bài chưa thử. Chạy lại script sau để tiếp tục.")
    if failed:
        print(f"\nDanh sách bài lỗi/dừng dở:")
        for f in failed:
            print(f"  - {f}")


if __name__ == "__main__":
    main()
