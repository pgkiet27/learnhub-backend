"""
Train & so sánh 3 model cho bài toán Churn Prediction:
    1. Logistic Regression — baseline đơn giản, dễ giải thích
    2. Random Forest       — ensemble bagging, xử lý tốt phi tuyến tính
    3. XGBoost             — ensemble boosting, thường cho kết quả tốt nhất

Xuất ra:
    - Bảng so sánh metrics (Accuracy, Precision, Recall, F1, AUC-ROC)
    - Confusion Matrix cho từng model (lưu ảnh .png)
    - Feature Importance của Random Forest & XGBoost (lưu ảnh .png)
    - Model tốt nhất (theo AUC-ROC) được lưu lại để dùng cho API

Chạy: python -m churn_prediction.train_model
"""
import pathlib
import json
import datetime

import joblib
import matplotlib
matplotlib.use("Agg")  # không cần hiển thị GUI, chỉ lưu file ảnh
import matplotlib.pyplot as plt
import seaborn as sns

from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score, f1_score,
    roc_auc_score, confusion_matrix, roc_curve,
)
from xgboost import XGBClassifier

from churn_prediction.data_loader import get_train_test_split

MODELS_DIR = pathlib.Path(__file__).parent / "models"
MODELS_DIR.mkdir(exist_ok=True)

VERSIONS_DIR = MODELS_DIR / "versions"
VERSIONS_DIR.mkdir(exist_ok=True)

TRAINING_HISTORY_PATH = MODELS_DIR / "training_history.json"

# MLOps bản rút gọn: ngưỡng AUC-ROC tối thiểu chấp nhận được. Nếu 1 lần train
# cho ra model dưới ngưỡng này, in cảnh báo rõ ràng thay vì âm thầm deploy
# model kém. (Bản đầy đủ theo PDF sẽ dùng SageMaker Model Monitor +
# EventBridge để tự động phát hiện + trigger retrain — ở quy mô khóa luận,
# nhóm làm bản rút gọn: log lại lịch sử + cảnh báo thủ công khi kém.)
MIN_ACCEPTABLE_AUC = 0.70


def log_training_run(best_metrics: dict, all_metrics: list, version_id: str) -> None:
    """
    Ghi lại 1 lần train vào training_history.json — mô phỏng việc "giám sát"
    của MLOps: biết được model nào, lúc nào, đạt kết quả ra sao, thay vì
    train xong rồi quên luôn không ai theo dõi.
    """
    history = []
    if TRAINING_HISTORY_PATH.exists():
        with open(TRAINING_HISTORY_PATH, encoding="utf-8") as f:
            history = json.load(f)

    history.append({
        "version_id": version_id,
        "trained_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "best_model": best_metrics["model"],
        "best_auc_roc": best_metrics["auc_roc"],
        "all_metrics": all_metrics,
        "below_threshold": best_metrics["auc_roc"] < MIN_ACCEPTABLE_AUC,
    })

    with open(TRAINING_HISTORY_PATH, "w", encoding="utf-8") as f:
        json.dump(history, f, indent=2, ensure_ascii=False)

    if best_metrics["auc_roc"] < MIN_ACCEPTABLE_AUC:
        print(
            f"\n⚠️  CẢNH BÁO: AUC-ROC ({best_metrics['auc_roc']:.4f}) dưới ngưỡng "
            f"chấp nhận được ({MIN_ACCEPTABLE_AUC}) — nên xem lại data/model "
            f"trước khi dùng cho production."
        )
    else:
        print(f"\n✅ Model đạt ngưỡng chất lượng (AUC-ROC ≥ {MIN_ACCEPTABLE_AUC})")


def evaluate_model(name, y_test, y_pred, y_proba):
    """Tính đầy đủ các chỉ số đánh giá cho 1 model."""
    return {
        "model": name,
        "accuracy": accuracy_score(y_test, y_pred),
        "precision": precision_score(y_test, y_pred),
        "recall": recall_score(y_test, y_pred),
        "f1_score": f1_score(y_test, y_pred),
        "auc_roc": roc_auc_score(y_test, y_proba),
    }


def plot_confusion_matrices(results_data, save_path):
    """Vẽ confusion matrix cho cả 3 model trên cùng 1 hình."""
    fig, axes = plt.subplots(1, 3, figsize=(15, 4))
    for ax, (name, y_test, y_pred) in zip(axes, results_data):
        cm = confusion_matrix(y_test, y_pred)
        sns.heatmap(
            cm, annot=True, fmt="d", cmap="Blues", ax=ax,
            xticklabels=["Không churn", "Churn"],
            yticklabels=["Không churn", "Churn"],
        )
        ax.set_title(name)
        ax.set_xlabel("Dự đoán")
        ax.set_ylabel("Thực tế")
    plt.tight_layout()
    plt.savefig(save_path, dpi=120)
    plt.close()
    print(f"Đã lưu confusion matrix: {save_path}")


def plot_roc_curves(results_data_proba, save_path):
    """Vẽ ROC curve của cả 3 model trên cùng 1 biểu đồ để so sánh trực quan."""
    plt.figure(figsize=(6, 6))
    for name, y_test, y_proba in results_data_proba:
        fpr, tpr, _ = roc_curve(y_test, y_proba)
        auc = roc_auc_score(y_test, y_proba)
        plt.plot(fpr, tpr, label=f"{name} (AUC={auc:.3f})")
    plt.plot([0, 1], [0, 1], "k--", label="Random (AUC=0.5)")
    plt.xlabel("False Positive Rate")
    plt.ylabel("True Positive Rate")
    plt.title("ROC Curve — So sánh 3 model")
    plt.legend()
    plt.tight_layout()
    plt.savefig(save_path, dpi=120)
    plt.close()
    print(f"Đã lưu ROC curve: {save_path}")


def plot_feature_importance(model, feature_names, model_name, save_path, top_n=15):
    """Vẽ top N feature quan trọng nhất — chỉ áp dụng cho model dạng tree (RF, XGBoost)."""
    importances = model.feature_importances_
    idx = importances.argsort()[-top_n:][::-1]

    plt.figure(figsize=(8, 6))
    plt.barh([feature_names[i] for i in idx][::-1], importances[idx][::-1])
    plt.xlabel("Mức độ quan trọng")
    plt.title(f"Top {top_n} Feature quan trọng nhất — {model_name}")
    plt.tight_layout()
    plt.savefig(save_path, dpi=120)
    plt.close()
    print(f"Đã lưu feature importance: {save_path}")


def main(drift: float = 0.0):
    print("Đang load dữ liệu..." + (f" (mô phỏng drift={drift})" if drift else ""))
    X_train, X_test, y_train, y_test = get_train_test_split(drift=drift)
    feature_names = list(X_train.columns)
    print(f"Train: {len(X_train)} dòng | Test: {len(X_test)} dòng | {len(feature_names)} feature\n")

    all_metrics = []
    cm_data = []
    roc_data = []

    # ---------- 1. Logistic Regression ----------
    # LR nhạy cảm với scale của feature -> cần chuẩn hóa (StandardScaler) trước.
    print("--- Training Logistic Regression ---")
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)

    lr_model = LogisticRegression(max_iter=1000, random_state=42)
    lr_model.fit(X_train_scaled, y_train)
    lr_pred = lr_model.predict(X_test_scaled)
    lr_proba = lr_model.predict_proba(X_test_scaled)[:, 1]

    all_metrics.append(evaluate_model("Logistic Regression", y_test, lr_pred, lr_proba))
    cm_data.append(("Logistic Regression", y_test, lr_pred))
    roc_data.append(("Logistic Regression", y_test, lr_proba))

    # ---------- 2. Random Forest ----------
    print("--- Training Random Forest ---")
    rf_model = RandomForestClassifier(n_estimators=200, random_state=42, n_jobs=-1)
    rf_model.fit(X_train, y_train)
    rf_pred = rf_model.predict(X_test)
    rf_proba = rf_model.predict_proba(X_test)[:, 1]

    all_metrics.append(evaluate_model("Random Forest", y_test, rf_pred, rf_proba))
    cm_data.append(("Random Forest", y_test, rf_pred))
    roc_data.append(("Random Forest", y_test, rf_proba))

    # ---------- 3. XGBoost ----------
    print("--- Training XGBoost ---")
    xgb_model = XGBClassifier(
        n_estimators=200, random_state=42, eval_metric="logloss", n_jobs=-1
    )
    xgb_model.fit(X_train, y_train)
    xgb_pred = xgb_model.predict(X_test)
    xgb_proba = xgb_model.predict_proba(X_test)[:, 1]

    all_metrics.append(evaluate_model("XGBoost", y_test, xgb_pred, xgb_proba))
    cm_data.append(("XGBoost", y_test, xgb_pred))
    roc_data.append(("XGBoost", y_test, xgb_proba))

    # ---------- So sánh & xuất kết quả ----------
    print("\n" + "=" * 70)
    print("BẢNG SO SÁNH KẾT QUẢ")
    print("=" * 70)
    header = f"{'Model':<22}{'Accuracy':<11}{'Precision':<11}{'Recall':<11}{'F1':<11}{'AUC-ROC':<10}"
    print(header)
    print("-" * 70)
    for m in all_metrics:
        print(
            f"{m['model']:<22}{m['accuracy']:<11.4f}{m['precision']:<11.4f}"
            f"{m['recall']:<11.4f}{m['f1_score']:<11.4f}{m['auc_roc']:<10.4f}"
        )

    with open(MODELS_DIR / "metrics.json", "w", encoding="utf-8") as f:
        json.dump(all_metrics, f, indent=2, ensure_ascii=False)
    print(f"\nĐã lưu metrics: {MODELS_DIR / 'metrics.json'}")

    plot_confusion_matrices(cm_data, MODELS_DIR / "confusion_matrices.png")
    plot_roc_curves(roc_data, MODELS_DIR / "roc_curves.png")
    plot_feature_importance(rf_model, feature_names, "Random Forest", MODELS_DIR / "feature_importance_rf.png")
    plot_feature_importance(xgb_model, feature_names, "XGBoost", MODELS_DIR / "feature_importance_xgb.png")

    # ---------- Lưu model tốt nhất (theo AUC-ROC) ----------
    best = max(all_metrics, key=lambda m: m["auc_roc"])
    print(f"\n🏆 Model tốt nhất: {best['model']} (AUC-ROC = {best['auc_roc']:.4f})")

    models_map = {"Logistic Regression": lr_model, "Random Forest": rf_model, "XGBoost": xgb_model}

    # "best_model.pkl" luôn là model ĐANG DÙNG (API predict.py đọc file này).
    joblib.dump(models_map[best["model"]], MODELS_DIR / "best_model.pkl")
    joblib.dump(scaler, MODELS_DIR / "scaler.pkl")
    joblib.dump(feature_names, MODELS_DIR / "feature_names.pkl")
    # Trung bình từng feature trên tập train: predict.py dùng để điền feature thiếu
    # (điền 0 là sai vì 0 mang nghĩa "không học/không xem", làm score bị đẩy lệch).
    joblib.dump(X_train.mean().to_dict(), MODELS_DIR / "feature_means.pkl")
    with open(MODELS_DIR / "best_model_name.txt", "w") as f:
        f.write(best["model"])

    # Versioning (MLOps rút gọn): lưu thêm 1 bản có timestamp trong versions/,
    # không bao giờ bị ghi đè -> có thể quay lại bản cũ nếu bản mới tệ hơn.
    version_id = datetime.datetime.now().strftime("v%Y%m%d_%H%M%S")
    version_path = VERSIONS_DIR / f"{version_id}_{best['model'].replace(' ', '_')}.pkl"
    joblib.dump(models_map[best["model"]], version_path)
    print(f"Đã lưu bản version: {version_path}")

    log_training_run(best, all_metrics, version_id)

    print(f"Đã lưu model tốt nhất vào: {MODELS_DIR / 'best_model.pkl'}")
    print("\nHOÀN TẤT.")


if __name__ == "__main__":
    main()
