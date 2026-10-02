"""
Package churn_prediction — Phần 4: Student Churn Prediction.
"""
import sys

# Windows console mặc định dùng cp1252, không in được tiếng Việt.
# Ép stdout/stderr sang UTF-8 ngay khi import package, áp dụng cho mọi
# script con (data_loader.py, train_model.py...) mà không cần lặp lại.
if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
