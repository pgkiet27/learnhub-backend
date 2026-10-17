"""
Load & chuẩn bị dữ liệu cho bài toán Churn Prediction.

Data nguồn: Synthetic (giả lập) — do chính nhóm sinh ra (xem synthetic_data.py),
khớp CHÍNH XÁC 100% với 8 feature đã thiết kế cho LearnHub
(days_since_last_login, watch_percentage_last_week...).

Đây là chiến lược "Cold Start" — dùng khi LearnHub chưa có user thật để thu
thập data hành vi. Khi có data thật, thay hàm generate_synthetic_churn_data()
bằng hàm lấy data thật từ DB, phần còn lại của pipeline giữ nguyên.
"""
from sklearn.model_selection import train_test_split

from churn_prediction.synthetic_data import generate_synthetic_churn_data


def get_train_test_split(
    test_size: float = 0.2, random_state: int = 42, n_students: int = 3000, drift: float = 0.0
):
    """
    Trả về X_train, X_test, y_train, y_test từ data giả lập (synthetic) —
    đã stratify theo churn (giữ đúng tỉ lệ 2 lớp giữa train/test).
    drift > 0: dùng data mô phỏng hành vi đã thay đổi (xem synthetic_data.py).
    """
    df = generate_synthetic_churn_data(
        n_students=n_students, random_state=random_state, drift=drift
    )
    X = df.drop(columns=["churn"])
    y = df["churn"]

    return train_test_split(
        X, y, test_size=test_size, random_state=random_state, stratify=y
    )


if __name__ == "__main__":
    X_train, X_test, y_train, y_test = get_train_test_split()
    print(f"Train: {len(X_train)} dòng | Test: {len(X_test)} dòng | {len(X_train.columns)} feature")
    print(f"Tỉ lệ churn (train): {y_train.mean():.2%}")
    print(f"Tỉ lệ churn (test):  {y_test.mean():.2%}")
