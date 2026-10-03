"""
SageMaker inference entry point for the churn model. Reuses predict.py, so the endpoint applies exactly
the same preprocessing (feature order, mean imputation, scaling) and risk thresholds as ai-service.

Packaging: model.tar.gz holds the files of one training run (best_model.pkl, scaler.pkl,
feature_names.pkl, feature_means.pkl, best_model_name.txt); source_dir=churn_prediction/ and
entry_point=sagemaker_inference.py, on a scikit-learn container with the same scikit-learn version
as requirements.txt. Set the CHURN_MODEL_VERSION environment variable on the SageMaker Model to the
Model Registry version (e.g. "churn-model/3"): it is returned as model_version and stored with every
prediction in enrollment-service's churn_feature_snapshots.

Request  (application/json): {"instances": [{"id": "<enrollment id>", "features": {"days_since_last_login": 3.0, ...}}]}
Response (application/json): {"model_version": "...", "predictions": [{"id", "churn_score", "churn_label",
                              "risk_level", "missing_features", "warnings"}, ...]}
"""
import json
import os

try:
    from churn_prediction.predict import load_artifacts, predict_with
except ImportError:  # in the SageMaker container, source_dir (churn_prediction/) itself is on sys.path
    from predict import load_artifacts, predict_with

RESULT_FIELDS = ("churn_score", "churn_label", "risk_level", "missing_features", "warnings")


def model_fn(model_dir):
    return load_artifacts(model_dir, os.environ.get("CHURN_MODEL_VERSION"))


def input_fn(request_body, content_type):
    if not content_type.startswith("application/json"):
        raise ValueError(f"Unsupported content type: {content_type}")
    return json.loads(request_body)


def predict_fn(data, artifacts):
    predictions = []
    for instance in data["instances"]:
        result = predict_with(artifacts, instance["features"])
        predictions.append({"id": instance["id"], **{k: result[k] for k in RESULT_FIELDS}})
    return {"model_version": artifacts.model_version, "predictions": predictions}


def output_fn(prediction, accept):
    return json.dumps(prediction), "application/json"
