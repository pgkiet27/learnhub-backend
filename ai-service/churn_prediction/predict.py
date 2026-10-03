"""
Dự đoán churn cho 1 học viên — load model đã train (best_model.pkl) và trả về
churn_score (xác suất bỏ học) + mức độ rủi ro.

Model hiện train trên data SYNTHETIC (giả lập, khớp đúng 8 feature LearnHub
đã thiết kế) — xem synthetic_data.py. Khi có data hành vi THẬT của LearnHub,
chỉ cần chạy lại `train_model.py` với data thật (cùng format 8 feature này),
pipeline predict dưới đây giữ nguyên không đổi.
"""
import pathlib
from dataclasses import dataclass

import joblib
import pandas as pd

MODELS_DIR = pathlib.Path(__file__).parent / "models"

# Ngưỡng dùng CHUNG cho risk_level và churn_label để không mâu thuẫn nhau.
# HIGH_RISK_THRESHOLD = 0.7 chính là ngưỡng trigger email nhắc nhở trong PDF.
HIGH_RISK_THRESHOLD = 0.7
MEDIUM_RISK_THRESHOLD = 0.4

# Khoảng giá trị hợp lý cho từng feature — dùng để CẢNH BÁO (không chặn cứng)
# khi input bất thường, vì model được train trên data nằm trong các khoảng
# này; input ngoài khoảng vẫn cho ra dự đoán nhưng độ tin cậy thấp hơn nhiều.
FEATURE_RANGES = {
    "days_since_last_login": (0, 365),
    "watch_percentage_last_week": (0, 100),
    "days_since_last_lesson": (0, 365),
    "current_course_progress": (0, 100),
    "quiz_failure_count": (0, 100),
    "login_frequency_trend": (-1, 1),
    "support_tickets_opened": (0, 100),
    "days_to_complete_last_lesson": (0, 365),
}


@dataclass(frozen=True)
class ChurnArtifacts:
    """Files of one training run; also what the SageMaker container loads (see sagemaker_inference.py)."""
    model: object
    scaler: object
    feature_names: list
    feature_means: dict
    model_name: str
    # Model Registry version when served by SageMaker, otherwise the model name
    model_version: str


# Lazy-load: chỉ đọc file model 1 lần, dùng lại cho các lần gọi sau
_local_artifacts = None


def _validate_features(features: dict) -> list:
    """Kiểm tra giá trị feature có nằm trong khoảng hợp lý không.
    Trả về list cảnh báo (KHÔNG raise lỗi cứng) — vẫn cho dự đoán nhưng
    người gọi API biết kết quả có thể kém tin cậy."""
    warnings = []
    for key, value in features.items():
        if key not in FEATURE_RANGES:
            continue
        try:
            value = float(value)
        except (TypeError, ValueError):
            warnings.append(f"{key}={value!r} không phải số hợp lệ, đã bỏ qua")
            continue
        lo, hi = FEATURE_RANGES[key]
        if value < lo or value > hi:
            warnings.append(f"{key}={value} nằm ngoài khoảng hợp lý [{lo}, {hi}]")
    return warnings


def load_artifacts(model_dir: pathlib.Path, model_version: str | None = None) -> ChurnArtifacts:
    model_dir = pathlib.Path(model_dir)
    if not (model_dir / "best_model.pkl").exists():
        raise FileNotFoundError(
            "Chưa có model nào được train. Chạy `python -m churn_prediction.train_model` trước."
        )

    means_path = model_dir / "feature_means.pkl"
    model_name = (model_dir / "best_model_name.txt").read_text(encoding="utf-8").strip()
    return ChurnArtifacts(
        model=joblib.load(model_dir / "best_model.pkl"),
        scaler=joblib.load(model_dir / "scaler.pkl"),
        feature_names=joblib.load(model_dir / "feature_names.pkl"),
        # Model train từ bản cũ chưa có file này -> fallback điền 0 như trước
        feature_means=joblib.load(means_path) if means_path.exists() else {},
        model_name=model_name,
        model_version=model_version or model_name,
    )


def _get_local_artifacts() -> ChurnArtifacts:
    global _local_artifacts
    if _local_artifacts is None:
        _local_artifacts = load_artifacts(MODELS_DIR)
    return _local_artifacts


def _risk_level(score: float) -> str:
    if score >= HIGH_RISK_THRESHOLD:
        return "high"
    elif score >= MEDIUM_RISK_THRESHOLD:
        return "medium"
    return "low"


def predict_churn(features: dict) -> dict:
    """Dự đoán churn cho 1 học viên bằng model local (thư mục models/)."""
    return predict_with(_get_local_artifacts(), features)


def predict_with(artifacts: ChurnArtifacts, features: dict) -> dict:
    """
    Dự đoán churn cho 1 học viên.

    Args:
        features: dict {tên_feature: giá trị}, gồm 8 key:
            days_since_last_login, watch_percentage_last_week,
            days_since_last_lesson, current_course_progress,
            quiz_failure_count, login_frequency_trend,
            support_tickets_opened, days_to_complete_last_lesson
            Feature nào không truyền sẽ tự động điền bằng giá trị TRUNG BÌNH
            của data lúc train — được liệt kê trong "missing_features". Feature có
            giá trị bất thường (âm, quá lớn, không phải số...) vẫn được dùng
            để dự đoán nhưng được liệt kê trong "warnings".

    Returns:
        dict {churn_score, churn_label, risk_level, model_used,
              missing_features, warnings}
    """
    missing = [f for f in artifacts.feature_names if f not in features]
    warnings = _validate_features(features)

    row = {}
    for f in artifacts.feature_names:
        # Thiếu / không hợp lệ -> điền TRUNG BÌNH lúc train (không phải 0: với
        # feature như % xem video, 0 nghĩa là "không học", làm score lệch hẳn).
        fallback = artifacts.feature_means.get(f, 0.0)
        value = features.get(f, fallback)
        try:
            row[f] = float(value)
        except (TypeError, ValueError):
            row[f] = float(fallback)  # đã cảnh báo ở warnings

    X = pd.DataFrame([row], columns=artifacts.feature_names)

    # Logistic Regression cần dữ liệu đã chuẩn hóa (StandardScaler) vì lúc
    # train cũng chuẩn hóa; Random Forest/XGBoost không cần.
    X_input = artifacts.scaler.transform(X) if artifacts.model_name == "Logistic Regression" else X

    churn_proba = float(artifacts.model.predict_proba(X_input)[0, 1])

    return {
        "churn_score": round(churn_proba, 4),
        "churn_label": churn_proba >= HIGH_RISK_THRESHOLD,  # True = nguy cơ cao, cần can thiệp
        "risk_level": _risk_level(churn_proba),
        "model_used": artifacts.model_version,
        "missing_features": missing,
        "warnings": warnings,
    }


if __name__ == "__main__":
    print("--- Học viên có nguy cơ CAO (lâu không đăng nhập, progress thấp, hay fail quiz) ---")
    at_risk_student = {
        "days_since_last_login": 45,
        "watch_percentage_last_week": 5,
        "days_since_last_lesson": 50,
        "current_course_progress": 15,
        "quiz_failure_count": 6,
        "login_frequency_trend": -0.8,
        "support_tickets_opened": 2,
        "days_to_complete_last_lesson": 12,
    }
    print(predict_churn(at_risk_student))

    print("\n--- Học viên gắn kết TỐT (đăng nhập đều, progress cao, ít fail quiz) ---")
    engaged_student = {
        "days_since_last_login": 1,
        "watch_percentage_last_week": 95,
        "days_since_last_lesson": 1,
        "current_course_progress": 88,
        "quiz_failure_count": 0,
        "login_frequency_trend": 0.6,
        "support_tickets_opened": 0,
        "days_to_complete_last_lesson": 1.5,
    }
    print(predict_churn(engaged_student))
