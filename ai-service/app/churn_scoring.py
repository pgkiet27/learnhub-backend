"""
Where churn predictions come from:
    CHURN_MODEL_BACKEND=local      the model files in churn_prediction/models (default, also docker-compose)
    CHURN_MODEL_BACKEND=sagemaker  the SageMaker endpoint CHURN_SAGEMAKER_ENDPOINT deployed by the MLOps pipeline
Both return the same result shape, so enrollment-service does not know which one answered.
"""
import json
import logging
import os
from functools import lru_cache

import boto3
from botocore.config import Config
from botocore.exceptions import BotoCoreError, ClientError

from churn_prediction.predict import predict_churn

log = logging.getLogger(__name__)


class ChurnModelUnavailable(Exception):
    pass


def predict_batch(items: list[tuple[str, dict]]) -> tuple[str, list[dict]]:
    """
    items: [(id, features)]. Returns (model_used, [{id, churn_score, churn_label, risk_level,
    missing_features, warnings}]). Raises FileNotFoundError when the local model is not trained yet,
    ChurnModelUnavailable when the SageMaker endpoint cannot be reached.
    """
    if not items:
        return "", []
    if os.environ.get("CHURN_MODEL_BACKEND", "local") == "sagemaker":
        return _predict_sagemaker(items)
    predictions = [{"id": item_id, **predict_churn(features)} for item_id, features in items]
    return predictions[0]["model_used"], predictions


def _predict_sagemaker(items: list[tuple[str, dict]]) -> tuple[str, list[dict]]:
    endpoint = os.environ.get("CHURN_SAGEMAKER_ENDPOINT")
    if not endpoint:
        raise ChurnModelUnavailable("CHURN_SAGEMAKER_ENDPOINT is not set")
    payload = {"instances": [{"id": item_id, "features": features} for item_id, features in items]}
    try:
        response = _sagemaker_client().invoke_endpoint(
            EndpointName=endpoint,
            ContentType="application/json",
            Accept="application/json",
            Body=json.dumps(payload),
        )
        body = json.loads(response["Body"].read())
        return body["model_version"], body["predictions"]
    except (BotoCoreError, ClientError, ValueError, KeyError) as e:
        log.error("Churn prediction from SageMaker endpoint %s failed: %s", endpoint, e)
        raise ChurnModelUnavailable(f"SageMaker endpoint {endpoint} failed: {e}") from e


@lru_cache(maxsize=1)
def _sagemaker_client():
    # One attempt within enrollment-service's 60 s read timeout; a serverless cold start can take ~30 s.
    # enrollment-service keeps the day's features even when scoring fails.
    return boto3.client(
        "sagemaker-runtime",
        region_name=os.environ.get("AWS_REGION", "us-east-1"),
        config=Config(connect_timeout=5, read_timeout=50, retries={"total_max_attempts": 1}),
    )
