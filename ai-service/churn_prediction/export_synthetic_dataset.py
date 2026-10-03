"""
Simulated churn dataset in the same S3 layout and schema as enrollment-service's export
(features/dt=.../part-0.jsonl.gz, labels/dt=.../part-0.jsonl.gz), so the MLOps pipeline can be built
before real labels exist and later only switch its S3 prefix.

Each enrollment is simulated day by day like the real nightly job: a snapshot at 02:00 (features from
past activity, scored by the current local model, reminder queued if high risk), then that day's
activity. It therefore has the same quirks as real data: one row per enrollment per day until the course
is completed, NULL features, days where a service failed, unknown labels, reminder emails that bring
students back, and no labels yet for the last 14 days.

Run:    python -m churn_prediction.export_synthetic_dataset
Upload: aws s3 sync churn_prediction/data/synthetic_dataset s3://<bucket>/churn/synthetic/
"""
import datetime
import math
import pathlib
import shutil
import uuid

import numpy as np
import pandas as pd

from churn_prediction.predict import MODELS_DIR, load_artifacts, predict_with

OUTPUT_DIR = pathlib.Path(__file__).parent / "data" / "synthetic_dataset"
LABEL_HORIZON_DAYS = 14
REMINDER_COOLDOWN_DAYS = 7

# Same order as ChurnFeatureCalculator.FEATURE_NAMES in enrollment-service
FEATURE_NAMES = [
    "current_course_progress",
    "days_since_last_lesson",
    "watch_percentage_last_week",
    "days_to_complete_last_lesson",
    "days_since_last_login",
    "login_frequency_trend",
    "quiz_failure_count",
    "support_tickets_opened",
]
# Chance that a service is down for a whole nightly run, and the features it leaves NULL
SOURCE_FAILURE_RATE = {"identity": 0.02, "user": 0.02, "assessment": 0.02, "ai": 0.01}
SOURCE_FEATURES = {
    "identity": ("days_since_last_login", "login_frequency_trend"),
    "user": ("support_tickets_opened",),
    "assessment": ("quiz_failure_count",),
}
UNKNOWN_WATCH_RATE = 0.05   # recent lessons without a known video length
UNKNOWN_LABEL_RATE = 0.01   # last activity only after the window (last_accessed_at was overwritten)
OPT_OUT_RATE = 0.15         # reminders turned off: queued but never delivered


def _iso(ts: datetime.datetime) -> str:
    return ts.strftime("%Y-%m-%dT%H:%M:%SZ")


def _round2(x: float) -> float:
    return math.floor(x * 100 + 0.5) / 100


class _Enrollment:
    """Activity of one simulated student in one course; day 0 is the first snapshot date."""

    def __init__(self, rng: np.random.Generator, days: int):
        self.rng = rng
        self.engagement = rng.beta(2, 2)
        self.enrolled_day = int(rng.integers(-40, days - 3))
        self.enrolled_hour = rng.uniform(8, 23)
        self.total_lessons = int(rng.integers(30, 150))
        disengaged = 1 - self.engagement
        self.drop_day = (self.enrolled_day + int(rng.integers(0, 45))
                         if rng.random() < 0.85 * disengaged ** 1.2 else None)
        self.opted_out = rng.random() < OPT_OUT_RATE

        self.lessons_done = 0
        self.lesson_days = []        # (day, hour, watch %)
        self.login_days = set()
        self.last_login = None       # (day, hour)
        self.last_took = None
        self.quiz_failures = 0
        self.ticket_days = []
        self.completed_day = None
        self.last_queued = None
        self.delivered_days = []
        self.boost_until = -math.inf

    def is_enrolled_at_snapshot(self, day: int) -> bool:
        # Enrolled during an earlier day and not completed yet (the job skips completed enrollments)
        return self.enrolled_day < day and self.completed_day is None

    def features(self, day: int) -> dict:
        now = day + 2 / 24

        def since(d, hour):
            return min(365.0, max(0.0, _round2(now - (d + hour / 24))))

        recent = [w for d, _, w in self.lesson_days if d >= day - 7]
        if not recent:
            watch = 0.0
        elif self.rng.random() < UNKNOWN_WATCH_RATE:
            watch = None
        else:
            watch = _round2(float(np.mean(recent)))

        last14 = sum(1 for d in self.login_days if day - 14 <= d < day)
        prev14 = sum(1 for d in self.login_days if day - 28 <= d < day - 14)
        peak = max(last14, prev14)

        last_lesson = self.lesson_days[-1][:2] if self.lesson_days else (self.enrolled_day, self.enrolled_hour)
        last_login = self.last_login or (self.enrolled_day, self.enrolled_hour)
        return {
            "current_course_progress": _round2(min(100.0, self.lessons_done * 100 / self.total_lessons)),
            "days_since_last_lesson": since(*last_lesson),
            "watch_percentage_last_week": watch,
            "days_to_complete_last_lesson": self.last_took,
            "days_since_last_login": since(*last_login),
            "login_frequency_trend": -1.0 if peak == 0 else _round2((last14 - prev14) / peak),
            "quiz_failure_count": float(self.quiz_failures),
            "support_tickets_opened": float(sum(1 for d in self.ticket_days if day - 30 <= d < day)),
        }

    def queue_reminder(self, day: int) -> bool:
        if self.last_queued is not None and day - self.last_queued < REMINDER_COOLDOWN_DAYS:
            return False
        self.last_queued = day
        if not self.opted_out:
            self.delivered_days.append(day)
            self.boost_until = day + 2
        return True

    def live_day(self, day: int) -> None:
        rng, e = self.rng, self.engagement
        p_study = 0.15 + 0.6 * e if self.drop_day is None or day < self.drop_day else 0.02
        if day <= self.boost_until:
            p_study = max(p_study, 0.4)
        if rng.random() < p_study:
            hour = rng.uniform(8, 23)
            self.lessons_done += int(rng.integers(1, 4))
            self.lesson_days.append((day, hour, float(np.clip(rng.normal(45 + 50 * e, 15), 5, 100))))
            self.last_took = _round2(float(np.clip(rng.gamma(2, (1 - e) * 3 + 0.3), 0.05, 30)))
            self.quiz_failures += int(rng.poisson((1 - e) * 0.4))
            self.login_days.add(day)
            self.last_login = (day, hour)
            if self.lessons_done >= self.total_lessons:
                self.completed_day = day
        elif rng.random() < 0.15 * e:
            self.login_days.add(day)
            self.last_login = (day, rng.uniform(8, 23))
        if rng.random() < 0.03 * (1 - e) + 0.003:
            self.ticket_days.append(day)

    def label(self, day: int) -> tuple[int | None, bool]:
        window = range(day, day + LABEL_HORIZON_DAYS)
        if self.rng.random() < UNKNOWN_LABEL_RATE:
            churn = None
        else:
            churn = 0 if any(d in window for d, _, _ in self.lesson_days) else 1
        return churn, any(d in window for d in self.delivered_days)


def build_dataset(n_enrollments: int = 600, days: int = 60, end_date: datetime.date = None,
                  random_state: int = 42) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Snapshots for the `days` dates before end_date; labels only where the 14-day window has ended."""
    rng = np.random.default_rng(random_state)
    end_date = end_date or datetime.date.today()
    first_date = end_date - datetime.timedelta(days=days)
    artifacts = load_artifacts(MODELS_DIR)

    def new_id() -> str:
        return str(uuid.UUID(bytes=rng.bytes(16), version=4))

    failed_sources = [{s for s, rate in SOURCE_FAILURE_RATE.items() if rng.random() < rate} for _ in range(days)]
    for source in SOURCE_FAILURE_RATE:   # every case shows up at least once, even in a short simulation
        if not any(source in failed for failed in failed_sources):
            failed_sources[int(rng.integers(0, days))].add(source)
    failed_sources = [sorted(failed) for failed in failed_sources]
    users = [new_id() for _ in range(int(n_enrollments * 0.8))]
    courses = [new_id() for _ in range(12)]

    features_rows, label_rows = [], []
    for _ in range(n_enrollments):
        enrollment = _Enrollment(rng, days)
        enrollment_id, user_id, course_id = new_id(), str(rng.choice(users)), str(rng.choice(courses))
        enrolled_at = (datetime.datetime.combine(first_date, datetime.time())
                       + datetime.timedelta(days=enrollment.enrolled_day, hours=enrollment.enrolled_hour - 7))
        snapshot_days = []

        for day in range(min(enrollment.enrolled_day, 0), days):
            if day >= 0 and enrollment.is_enrolled_at_snapshot(day):
                snapshot_days.append(day)
                failed = failed_sources[day]
                features = enrollment.features(day)
                for source in failed:
                    for name in SOURCE_FEATURES.get(source, ()):
                        features[name] = None

                score = risk = model_used = None
                if "ai" not in failed:
                    result = predict_with(artifacts, {k: v for k, v in features.items() if v is not None})
                    score, risk, model_used = result["churn_score"], result["risk_level"], result["model_used"]
                queued = risk == "high" and enrollment.queue_reminder(day)

                snapshot_date = first_date + datetime.timedelta(days=day)
                snapshot_at = datetime.datetime.combine(snapshot_date, datetime.time()) - datetime.timedelta(hours=5)
                features_rows.append({
                    "snapshot_date": snapshot_date.isoformat(),
                    "enrollment_id": enrollment_id,
                    "snapshot_at": _iso(snapshot_at),
                    "user_id": user_id,
                    "course_id": course_id,
                    "enrolled_at": _iso(enrolled_at),
                    **{name: features[name] for name in FEATURE_NAMES},
                    "missing_sources": ",".join(failed) or None,
                    "churn_score": score,
                    "risk_level": risk,
                    "model_used": model_used,
                    "reminder_queued": queued,
                })
            if day >= enrollment.enrolled_day and enrollment.completed_day is None:
                enrollment.live_day(day)

        for day in snapshot_days:
            if day + LABEL_HORIZON_DAYS > days:
                continue
            churn, reminded = enrollment.label(day)
            snapshot_date = first_date + datetime.timedelta(days=day)
            snapshot_at = datetime.datetime.combine(snapshot_date, datetime.time()) - datetime.timedelta(hours=5)
            label_rows.append({
                "snapshot_date": snapshot_date.isoformat(),
                "enrollment_id": enrollment_id,
                "churn": churn,
                "reminded_in_window": reminded,
                "labeled_at": _iso(snapshot_at + datetime.timedelta(days=LABEL_HORIZON_DAYS)),
            })
    labels = pd.DataFrame(label_rows)
    labels["churn"] = labels["churn"].astype("Int64")   # 0/1/null like the real export, not 0.0/1.0
    return pd.DataFrame(features_rows), labels


def write_partitions(df: pd.DataFrame, dataset: str) -> None:
    for snapshot_date, part in df.groupby("snapshot_date"):
        path = OUTPUT_DIR / dataset / f"dt={snapshot_date}" / "part-0.jsonl.gz"
        path.parent.mkdir(parents=True, exist_ok=True)
        part.sort_values("enrollment_id").to_json(path, orient="records", lines=True, compression="gzip")


if __name__ == "__main__":
    features, labels = build_dataset()
    shutil.rmtree(OUTPUT_DIR, ignore_errors=True)
    write_partitions(features, "features")
    write_partitions(labels, "labels")

    known = labels["churn"].dropna()
    print(f"{len(features)} snapshots of {features['enrollment_id'].nunique()} enrollments, "
          f"{features['snapshot_date'].nunique()} feature dates, {labels['snapshot_date'].nunique()} labelled dates")
    print(f"Labels: {len(labels)} (churn rate {known.mean():.1%}, unknown {labels['churn'].isna().mean():.1%}, "
          f"reminded in window {labels['reminded_in_window'].mean():.1%})")
    print(f"Risk levels: {features['risk_level'].value_counts(dropna=False).to_dict()}")
    print("NULL share per feature:")
    print(features[FEATURE_NAMES].isna().mean().round(3).to_string())
    print(f"Written to {OUTPUT_DIR}")
