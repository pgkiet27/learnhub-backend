"""
Sinh dữ liệu GIẢ LẬP (synthetic) khớp CHÍNH XÁC với 8 feature đã thiết kế cho
Churn Prediction của LearnHub — dùng khi chưa có user thật để thu thập data.

Đây là chiến lược "Cold Start" đã đề cập từ đầu trong tài liệu kế hoạch:
    "Dùng synthetic data để train model ban đầu, sau khi có ~100 user thật
     thì train lại với real data."

CÁCH THIẾT KẾ ĐỂ DATA "THỰC TẾ" (không phải random vô nghĩa):
    Dùng 1 biến ẩn `engagement_score` (mức độ gắn kết, 0-1) làm gốc cho mỗi
    học viên giả lập. Toàn bộ 8 feature quan sát được VÀ nhãn churn đều được
    suy ra từ engagement_score này (+ nhiễu ngẫu nhiên riêng từng feature) —
    giống cách hành vi thật vận hành: 1 học viên gắn kết cao sẽ TỰ NHIÊN có
    đồng thời ít ngày vắng mặt, progress cao, ít fail quiz, xu hướng học tăng...
    chứ không phải các con số độc lập ngẫu nhiên với nhau.
"""
import pathlib
import numpy as np
import pandas as pd

OUTPUT_PATH = pathlib.Path(__file__).parent / "data" / "synthetic_churn_data.csv"

FEATURE_COLUMNS = [
    "days_since_last_login",
    "watch_percentage_last_week",
    "days_since_last_lesson",
    "current_course_progress",
    "quiz_failure_count",
    "login_frequency_trend",
    "support_tickets_opened",
    "days_to_complete_last_lesson",
]


def generate_synthetic_churn_data(
    n_students: int = 3000, random_state: int = 42, drift: float = 0.0
) -> pd.DataFrame:
    """
    Sinh n_students học viên giả lập, đủ 8 feature + nhãn churn (0/1).

    Args:
        n_students: số lượng học viên giả lập muốn sinh ra
        random_state: seed cố định -> chạy lại nhiều lần luôn ra data giống nhau
        drift: 0.0 = hành vi như lúc train ban đầu. Trong khoảng (0, 1] mô phỏng
               "data drift": hành vi học viên thay đổi theo thời gian (VD: sau
               1 tháng, lý do bỏ học chuyển từ "ít gắn kết" sang "gặp vấn đề hỗ
               trợ") -> model cũ dự đoán kém đi. Dùng để demo giám sát/retrain
               trong check_model_health.py.

    Returns:
        DataFrame với 8 cột feature + cột "churn"
    """
    rng = np.random.default_rng(random_state)

    # Biến ẩn: mức độ gắn kết của từng học viên (0 = hoàn toàn không gắn kết,
    # 1 = cực kỳ gắn kết). Phân phối Beta(2,2) -> tập trung quanh giữa, có
    # đủ học viên ở 2 thái cực để model học được ranh giới rõ ràng.
    engagement = rng.beta(2, 2, n_students)

    # 1. days_since_last_login — gắn kết cao -> số ngày vắng mặt THẤP
    days_since_last_login = np.clip(
        (1 - engagement) * 45 + rng.normal(0, 5, n_students), 0, 90
    ).round().astype(int)

    # 2. watch_percentage_last_week — gắn kết cao -> % xem video tuần qua CAO
    watch_percentage_last_week = np.clip(
        engagement * 90 + rng.normal(0, 10, n_students), 0, 100
    ).round(1)

    # 3. days_since_last_lesson — tương quan với days_since_last_login,
    #    nhưng thêm nhiễu riêng (có thể đăng nhập mà không học bài mới)
    days_since_last_lesson = np.clip(
        days_since_last_login + rng.normal(2, 4, n_students), 0, 90
    ).round().astype(int)

    # 4. current_course_progress — gắn kết cao -> tiến độ khóa học CAO
    current_course_progress = np.clip(
        engagement * 85 + rng.normal(0, 12, n_students), 0, 100
    ).round(1)

    # 5. quiz_failure_count — gắn kết thấp -> dễ nản, fail quiz nhiều hơn
    #    (dùng phân phối Poisson vì đây là dạng "đếm số lần", không âm)
    quiz_failure_lambda = np.clip((1 - engagement) * 4 + 0.3, 0.1, None)
    quiz_failure_count = rng.poisson(quiz_failure_lambda)

    # 6. login_frequency_trend — gắn kết thấp -> xu hướng đăng nhập ĐANG GIẢM
    #    (giá trị âm = giảm dần, dương = tăng dần, khoảng [-1, 1])
    login_frequency_trend = np.clip(
        (engagement - 0.5) * 2 + rng.normal(0, 0.3, n_students), -1, 1
    ).round(2)

    # 7. support_tickets_opened — học viên gặp khó khăn (gắn kết thấp) mở
    #    ticket hỗ trợ nhiều hơn 1 chút (tín hiệu "đang gặp vấn đề")
    support_lambda = np.clip((1 - engagement) * 1.2, 0.05, None)
    support_tickets_opened = rng.poisson(support_lambda)

    # 8. days_to_complete_last_lesson — gắn kết thấp -> học CHẬM hơn
    days_to_complete_last_lesson = np.clip(
        (1 - engagement) * 10 + 1 + rng.normal(0, 2, n_students), 0.5, 30
    ).round(1)

    # Nhãn churn: chủ yếu quyết định bởi engagement (ẩn), qua hàm sigmoid
    # (logistic) + nhiễu ngẫu nhiên riêng -> KHÔNG phải ngưỡng cứng, để
    # model thực sự phải "học" ranh giới thay vì phân loại hoàn hảo 100%
    # (nếu 100% chính xác thì kết quả sẽ trông đáng ngờ/giả tạo).
    # drift > 0: giảm ảnh hưởng của engagement, tăng ảnh hưởng của số ticket
    # hỗ trợ (quan hệ giữa feature và churn đã đổi so với lúc model được train).
    # drift = 0 cho công thức y hệt bản gốc, không đổi thứ tự sinh số ngẫu nhiên.
    churn_logit = (
        (1 - engagement) * 7 * (1 - drift)
        - 4
        + drift * 3.5                                        # bù trừ để tỉ lệ churn trung bình không đổi
        + drift * (support_tickets_opened - 0.6) * 2.5      # 0.6 ~ số ticket trung bình
        + rng.normal(0, 0.8, n_students)
    )
    churn_probability = 1 / (1 + np.exp(-churn_logit))
    churn = rng.binomial(1, churn_probability)

    df = pd.DataFrame({
        "days_since_last_login": days_since_last_login,
        "watch_percentage_last_week": watch_percentage_last_week,
        "days_since_last_lesson": days_since_last_lesson,
        "current_course_progress": current_course_progress,
        "quiz_failure_count": quiz_failure_count,
        "login_frequency_trend": login_frequency_trend,
        "support_tickets_opened": support_tickets_opened,
        "days_to_complete_last_lesson": days_to_complete_last_lesson,
        "churn": churn,
    })
    return df


def save_synthetic_data(n_students: int = 3000, random_state: int = 42) -> pathlib.Path:
    """Sinh data rồi lưu ra CSV — để tiện xem lại bằng Excel/pandas nếu cần."""
    df = generate_synthetic_churn_data(n_students, random_state)
    OUTPUT_PATH.parent.mkdir(exist_ok=True)
    df.to_csv(OUTPUT_PATH, index=False)
    return OUTPUT_PATH


if __name__ == "__main__":
    df = generate_synthetic_churn_data()
    print(f"Đã sinh {len(df)} học viên giả lập, {len(FEATURE_COLUMNS)} feature")
    print(f"\nTỉ lệ churn: {df['churn'].mean():.2%}")
    print(f"\n5 dòng đầu:")
    print(df.head())
    print(f"\nThống kê mô tả:")
    print(df.describe().round(2))

    path = save_synthetic_data()
    print(f"\nĐã lưu vào: {path}")
