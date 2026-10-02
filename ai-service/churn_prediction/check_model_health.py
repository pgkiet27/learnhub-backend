"""
GIÁM SÁT MODEL & TỰ ĐỘNG RETRAIN (MLOps bản rút gọn)

Mô phỏng quy trình trong PDF (SageMaker Model Monitor + EventBridge) ở quy mô
khóa luận, chạy hoàn toàn local:

    1. Lấy dữ liệu MỚI (data hiện tại của học viên)
    2. Đo lại chất lượng model ĐANG DÙNG (best_model.pkl) trên dữ liệu mới
    3. Nếu AUC-ROC < ngưỡng, hoặc tụt quá nhiều so với lúc train  -> "drift"
    4. Retrain 3 model trên dữ liệu mới, lưu thành version mới
    5. Cổng duyệt (thay cho "Pending manual approval" của Model Registry):
       chỉ triển khai model mới nếu tốt hơn model cũ trên CÙNG tập dữ liệu mới;
       nếu không thì tự động khôi phục model cũ.

Mọi lần kiểm tra đều được ghi vào models/health_log.json.

Cách chạy:
    python -m churn_prediction.check_model_health                    # kiểm tra bình thường
    python -m churn_prediction.check_model_health --simulate-drift   # demo: hành vi học viên đã đổi
    python -m churn_prediction.check_model_health --simulate-drift 0.9
    python -m churn_prediction.check_model_health --no-retrain       # chỉ kiểm tra, không train lại

Lưu ý: nếu API đang chạy, cần restart để nạp model mới (predict.py cache model).
"""
import argparse
import datetime
import json
import shutil
import tempfile

import joblib
import pandas as pd
from sklearn.metrics import roc_auc_score

from churn_prediction import train_model
from churn_prediction.synthetic_data import generate_synthetic_churn_data

MODELS_DIR = train_model.MODELS_DIR
HEALTH_LOG_PATH = MODELS_DIR / "health_log.json"
MIN_ACCEPTABLE_AUC = train_model.MIN_ACCEPTABLE_AUC
MAX_AUC_DROP = 0.05  # tụt quá 0.05 so với lúc train cũng coi là suy giảm

# Các file cấu thành 1 model đang triển khai (cần sao lưu/khôi phục cùng nhau)
ACTIVE_FILES = ["best_model.pkl", "scaler.pkl", "feature_names.pkl", "feature_means.pkl", "best_model_name.txt"]


def load_active_model():
    model = joblib.load(MODELS_DIR / "best_model.pkl")
    scaler = joblib.load(MODELS_DIR / "scaler.pkl")
    feature_names = joblib.load(MODELS_DIR / "feature_names.pkl")
    name = (MODELS_DIR / "best_model_name.txt").read_text(encoding="utf-8").strip()
    return model, scaler, feature_names, name


def evaluate_active_model(df: pd.DataFrame) -> float:
    """AUC-ROC của model đang triển khai trên dữ liệu df (có cột 'churn')."""
    model, scaler, feature_names, name = load_active_model()
    X = df[feature_names]
    X_in = scaler.transform(X) if name == "Logistic Regression" else X
    return float(roc_auc_score(df["churn"], model.predict_proba(X_in)[:, 1]))


def last_trained_auc():
    if not train_model.TRAINING_HISTORY_PATH.exists():
        return None
    history = json.loads(train_model.TRAINING_HISTORY_PATH.read_text(encoding="utf-8"))
    return history[-1]["best_auc_roc"] if history else None


def append_health_log(entry: dict) -> None:
    log = []
    if HEALTH_LOG_PATH.exists():
        log = json.loads(HEALTH_LOG_PATH.read_text(encoding="utf-8"))
    log.append(entry)
    HEALTH_LOG_PATH.write_text(json.dumps(log, indent=2, ensure_ascii=False), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description="Giám sát & retrain model churn")
    parser.add_argument("--simulate-drift", nargs="?", const=0.7, type=float, default=0.0,
                        metavar="MỨC", help="mô phỏng hành vi học viên đã đổi (mặc định 0.7, khoảng 0-1)")
    parser.add_argument("--no-retrain", action="store_true", help="chỉ kiểm tra, không retrain")
    args = parser.parse_args()
    drift = args.simulate_drift

    if not (MODELS_DIR / "best_model.pkl").exists():
        print("Chưa có model nào. Chạy `python -m churn_prediction.train_model` trước.")
        return

    print("=" * 66)
    print("KIỂM TRA SỨC KHỎE MODEL CHURN")
    print("=" * 66)

    # Dữ liệu "mới" (seed khác lúc train để không trùng dữ liệu cũ)
    fresh = generate_synthetic_churn_data(n_students=1000, random_state=123, drift=drift)
    if drift:
        print(f"[Mô phỏng] Hành vi học viên đã thay đổi (drift = {drift})")
    print(f"Dữ liệu mới: {len(fresh)} học viên, tỉ lệ churn {fresh['churn'].mean():.1%}")

    _, _, _, model_name = load_active_model()
    auc_before = evaluate_active_model(fresh)
    auc_trained = last_trained_auc()

    print(f"\nModel đang dùng : {model_name}")
    if auc_trained is not None:
        print(f"AUC lúc train   : {auc_trained:.4f}")
    print(f"AUC trên data mới: {auc_before:.4f}   (ngưỡng tối thiểu: {MIN_ACCEPTABLE_AUC})")

    reasons = []
    if auc_before < MIN_ACCEPTABLE_AUC:
        reasons.append(f"AUC {auc_before:.4f} thấp hơn ngưỡng {MIN_ACCEPTABLE_AUC}")
    if auc_trained is not None and auc_trained - auc_before > MAX_AUC_DROP:
        reasons.append(f"AUC tụt {auc_trained - auc_before:.4f} so với lúc train (cho phép tối đa {MAX_AUC_DROP})")

    entry = {
        "checked_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "model": model_name,
        "simulated_drift": drift,
        "auc_trained": auc_trained,
        "auc_on_new_data": auc_before,
        "degraded": bool(reasons),
        "reasons": reasons,
        "action": "none",
    }

    if not reasons:
        print("\n✅ MODEL KHỎE — không cần retrain.")
        append_health_log(entry)
        return

    print("\n⚠️  PHÁT HIỆN MODEL SUY GIẢM (model drift):")
    for r in reasons:
        print(f"   - {r}")

    if args.no_retrain:
        print("\n(--no-retrain) Bỏ qua bước retrain.")
        entry["action"] = "skipped (--no-retrain)"
        append_health_log(entry)
        return

    # ---- Retrain, có sao lưu model cũ để khôi phục nếu model mới không tốt hơn ----
    print("\n>>> Bắt đầu RETRAIN trên dữ liệu mới...\n")
    backup_dir = tempfile.mkdtemp(prefix="churn_backup_")
    for fname in ACTIVE_FILES:
        shutil.copy2(MODELS_DIR / fname, backup_dir)

    train_model.main(drift=drift)

    auc_after = evaluate_active_model(fresh)
    print("\n" + "=" * 66)
    print(f"CỔNG DUYỆT MODEL MỚI (đo trên cùng tập dữ liệu mới)")
    print(f"   Model cũ : AUC {auc_before:.4f}")
    print(f"   Model mới: AUC {auc_after:.4f}")

    if auc_after > auc_before:
        print("✅ Model mới TỐT HƠN -> đã triển khai (best_model.pkl).")
        print("   Nếu API đang chạy, hãy restart để nạp model mới.")
        entry["action"] = "retrained & deployed"
    else:
        for fname in ACTIVE_FILES:
            shutil.copy2(f"{backup_dir}/{fname}", MODELS_DIR / fname)
        print("❌ Model mới KHÔNG tốt hơn -> đã KHÔI PHỤC model cũ.")
        entry["action"] = "retrained but rolled back"

    shutil.rmtree(backup_dir, ignore_errors=True)
    entry["auc_after_retrain"] = auc_after
    append_health_log(entry)
    print(f"\nĐã ghi log: {HEALTH_LOG_PATH}")


if __name__ == "__main__":
    main()
