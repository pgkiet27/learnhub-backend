"""
Giao diện Gradio để test RAG Chatbot + Tóm tắt bài học trực quan —
gõ câu hỏi tùy ý ngay trên trình duyệt, không cần sửa code/restart.

Chạy: python gradio_app.py
Mở trình duyệt: http://localhost:7860
"""
import uuid
import pathlib
import gradio as gr

from app.rag_query import ask_chatbot
from app.summarize import summarize_lesson
from app.db import get_connection, dict_cursor
from churn_prediction.predict import predict_churn

# Namespace/đường dẫn PHẢI khớp với index_real_courses.py để sinh đúng
# course_id/lesson_id đã dùng lúc index.
NAMESPACE = uuid.UUID("12345678-1234-5678-1234-567812345678")
BASE_DIR = pathlib.Path(r"C:\Users\ACER\Desktop\KLTN")

# user_id GIẢ ĐỊNH — dùng để test cơ chế lưu lịch sử chat (chatbot_sessions/
# chatbot_messages) khi CHƯA có hệ thống đăng nhập thật (việc của Kiệt bên
# Frontend/Identity Service). Khi tích hợp thật, giá trị này sẽ được thay bằng
# user_id thật của học viên đang đăng nhập — không cần sửa gì ở phần AI Service.
FAKE_USER_ID = "00000000-0000-0000-0000-000000000001"

COURSES = {
    "AWS CloudOps Engineer - Associate": BASE_DIR / "AWS CloudOps Engineer - Associate" / "English",
    "AWS Developer - Associate": BASE_DIR / "AWS Developer - Associate" / "English",
}


def get_indexed_lesson_ids() -> set:
    """Lấy tập lesson_id đã thực sự index trong DB — để lọc dropdown chỉ hiện bài dùng được."""
    with get_connection() as conn:
        cur = dict_cursor(conn)
        cur.execute("SELECT DISTINCT lesson_id FROM lesson_embeddings")
        return {str(r["lesson_id"]) for r in cur.fetchall()}


def build_course_options() -> dict:
    """{"Tên khóa học": course_id}"""
    return {name: str(uuid.uuid5(NAMESPACE, name)) for name in COURSES}


def build_lesson_options() -> dict:
    """{"Khóa / Section / Tên bài": lesson_id} — chỉ gồm bài đã index (quét lại
    thư mục nguồn theo đúng logic index_real_courses.py để lấy tên bài đẹp)."""
    indexed_ids = get_indexed_lesson_ids()
    options = {}
    for course_name, course_path in COURSES.items():
        if not course_path.exists():
            continue
        section_dirs = sorted(d for d in course_path.iterdir() if d.is_dir() and d.name != "Practice Labs")
        for section_dir in section_dirs:
            for md_file in sorted(section_dir.glob("*.md")):
                relative_key = f"{course_name}/{section_dir.name}/{md_file.name}"
                lesson_id = str(uuid.uuid5(NAMESPACE, relative_key))
                if lesson_id in indexed_ids:
                    label = f"{course_name} / {section_dir.name} / {md_file.stem}"
                    options[label] = lesson_id
    return options


COURSE_OPTIONS = build_course_options()
LESSON_OPTIONS = build_lesson_options()


def chat_fn(message: str, history, course_name: str) -> str:
    """Hàm xử lý cho gr.ChatInterface — mỗi câu hỏi độc lập (không dùng history
    làm ngữ cảnh, đúng thiết kế RAG: luôn retrieve lại từ đầu theo câu hỏi mới)."""
    print(f"\n[Chat] Câu hỏi   : {message}")
    print(f"[Chat] Khóa học  : {course_name}")

    if not course_name:
        print("[Chat] LỖI: chưa chọn khóa học")
        return "Vui lòng chọn khóa học trước khi hỏi."

    course_id = COURSE_OPTIONS[course_name]
    # Truyền FAKE_USER_ID -> ask_chatbot() sẽ tự lưu câu hỏi + câu trả lời
    # vào chatbot_sessions/chatbot_messages (xem app/chat_history.py)
    result = ask_chatbot(question=message, course_id=course_id, user_id=FAKE_USER_ID)
    answer = result["answer"]

    print(f"[Chat] Số nguồn tìm được : {len(result['sources'])}")
    for i, s in enumerate(result["sources"], 1):
        print(f"[Chat]   Nguồn {i}: similarity={s['similarity']} | {s['text_preview'][:80]}...")
    print(f"[Chat] Trả lời xong ({len(answer)} ký tự)")

    if result["sources"]:
        sources_text = "\n\n---\n**📚 Nguồn tham khảo:**\n"
        for i, s in enumerate(result["sources"], 1):
            sources_text += f"{i}. *(độ liên quan: {s['similarity']})* {s['text_preview']}\n"
        answer += sources_text

    return answer


def churn_predict_fn(
    days_since_last_login,
    watch_percentage_last_week,
    days_since_last_lesson,
    current_course_progress,
    quiz_failure_count,
    login_frequency_trend,
    support_tickets_opened,
    days_to_complete_last_lesson,
) -> str:
    """Nhận 8 giá trị từ slider/input, gọi predict_churn(), trả về kết quả dạng Markdown."""
    features = {
        "days_since_last_login": days_since_last_login,
        "watch_percentage_last_week": watch_percentage_last_week,
        "days_since_last_lesson": days_since_last_lesson,
        "current_course_progress": current_course_progress,
        "quiz_failure_count": quiz_failure_count,
        "login_frequency_trend": login_frequency_trend,
        "support_tickets_opened": support_tickets_opened,
        "days_to_complete_last_lesson": days_to_complete_last_lesson,
    }
    print(f"\n[Churn] Input features: {features}")

    result = predict_churn(features)
    print(f"[Churn] Kết quả: {result}")

    risk_emoji = {"high": "🔴", "medium": "🟡", "low": "🟢"}
    emoji = risk_emoji.get(result["risk_level"], "")

    return f"""
### {emoji} Kết quả dự đoán

| | |
|---|---|
| **Churn score** | {result['churn_score']:.2%} |
| **Mức độ rủi ro** | {result['risk_level'].upper()} |
| **Cần can thiệp (nguy cơ cao, ≥ 70%)?** | {"Có" if result['churn_label'] else "Không"} |
| **Model dùng** | {result['model_used']} |
"""


def summarize_fn(lesson_label: str) -> str:
    print(f"\n[Summarize] Bài học: {lesson_label}")
    if not lesson_label:
        print("[Summarize] LỖI: chưa chọn bài học")
        return "Vui lòng chọn 1 bài học."
    lesson_id = LESSON_OPTIONS[lesson_label]
    result = summarize_lesson(lesson_id)
    print(f"[Summarize] Tóm tắt xong ({len(result['summary'])} ký tự)")
    return result["summary"]


with gr.Blocks(title="LearnHub AI Service") as demo:
    gr.Markdown("# 🤖 LearnHub AI Service — RAG Chatbot & Tóm tắt bài học")
    gr.Markdown(f"*Đã index {len(LESSON_OPTIONS)} bài học để test.*")

    with gr.Tab("💬 Hỏi-đáp (Chatbot)"):
        course_dropdown = gr.Dropdown(
            choices=list(COURSE_OPTIONS.keys()),
            value=next(iter(COURSE_OPTIONS), None),
            label="Chọn khóa học",
        )
        gr.ChatInterface(
            fn=chat_fn,
            additional_inputs=[course_dropdown],
            examples=[
                ["What is AWS Lambda and how is it invoked?"],
                ["What is the difference between EBS and EFS?"],
                ["How does EC2 Auto Scaling work?"],
                ["Cách làm bánh mì Việt Nam truyền thống?"],  # câu hỏi ngoài phạm vi, test chatbot từ chối đúng cách
            ],
        )

    with gr.Tab("📝 Tóm tắt bài học"):
        lesson_dropdown = gr.Dropdown(
            choices=list(LESSON_OPTIONS.keys()),
            label=f"Chọn bài học ({len(LESSON_OPTIONS)} bài đã index)",
        )
        summarize_btn = gr.Button("Tóm tắt bài học này")
        summary_output = gr.Markdown()
        summarize_btn.click(fn=summarize_fn, inputs=lesson_dropdown, outputs=summary_output)

    with gr.Tab("📉 Dự đoán Churn"):
        gr.Markdown("Kéo thanh trượt để mô phỏng hành vi 1 học viên, xem model dự đoán nguy cơ bỏ học.")

        with gr.Row():
            with gr.Column():
                f_login = gr.Slider(0, 90, value=5, step=1, label="days_since_last_login (số ngày chưa đăng nhập)")
                f_watch = gr.Slider(0, 100, value=70, step=1, label="watch_percentage_last_week (% xem video tuần qua)")
                f_lesson = gr.Slider(0, 90, value=5, step=1, label="days_since_last_lesson (số ngày chưa học bài mới)")
                f_progress = gr.Slider(0, 100, value=50, step=1, label="current_course_progress (% tiến độ khóa học)")
            with gr.Column():
                f_quiz_fail = gr.Slider(0, 15, value=1, step=1, label="quiz_failure_count (số lần fail quiz)")
                f_trend = gr.Slider(-1, 1, value=0.0, step=0.1, label="login_frequency_trend (xu hướng đăng nhập: âm=giảm, dương=tăng)")
                f_tickets = gr.Slider(0, 10, value=0, step=1, label="support_tickets_opened (số ticket hỗ trợ đã mở)")
                f_complete = gr.Slider(0, 30, value=3, step=0.5, label="days_to_complete_last_lesson (số ngày hoàn thành bài gần nhất)")

        churn_btn = gr.Button("🔍 Dự đoán Churn", variant="primary")
        churn_output = gr.Markdown()

        churn_btn.click(
            fn=churn_predict_fn,
            inputs=[f_login, f_watch, f_lesson, f_progress, f_quiz_fail, f_trend, f_tickets, f_complete],
            outputs=churn_output,
        )

        gr.Examples(
            examples=[
                [45, 5, 50, 15, 6, -0.8, 2, 12],   # nguy cơ cao
                [1, 95, 1, 88, 0, 0.6, 0, 1.5],      # nguy cơ thấp
                [25, 35, 25, 30, 3, -0.2, 1, 8],      # trung bình (~58%, MEDIUM)
                [15, 50, 15, 45, 2, 0.0, 1, 5],       # thấp-biên (~33%, LOW)
            ],
            inputs=[f_login, f_watch, f_lesson, f_progress, f_quiz_fail, f_trend, f_tickets, f_complete],
            label="Ví dụ mẫu (bấm để điền nhanh)",
        )


if __name__ == "__main__":
    demo.launch()
