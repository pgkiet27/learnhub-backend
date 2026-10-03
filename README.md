# LearnHub Backend

Backend của **LearnHub** — nền tảng học trực tuyến tích hợp AI (khóa luận tốt nghiệp). Kiến trúc microservices:
7 service Spring Boot (Java 25) + 1 AI service (Python/FastAPI), giao tiếp qua REST nội bộ và RabbitMQ,
mọi request từ frontend đi qua một API Gateway.

```
Frontend (Next.js) ──► API Gateway :8080 ──► identity / user / course / enrollment / payment / assessment / ai
                                                     │             ▲
                                     RabbitMQ (learnhub.events)    │ REST nội bộ (/api/v1/internal/**)
                                                     ▼             │
                                          notification-service ────┘
```

## Các service

| Service | Port | Database | Chức năng |
|---|---|---|---|
| `api-gateway` | 8080 | — | Verify JWT, gắn `X-User-Id`/`X-User-Role`, route tới các service, CORS |
| `identity-service` | 8081 | identity_db (5433) | Đăng nhập qua AWS Cognito, cấp JWT LearnHub, ghi nhận hoạt động đăng nhập |
| `user-service` | 8082 | user_db (5434) | Hồ sơ, wishlist, cài đặt thông báo, ticket hỗ trợ |
| `course-service` | 8083 | course_db (5435) | Khóa học, bài học, Q&A, đánh giá, coupon, duyệt khóa học, upload S3 |
| `enrollment-service` | 8084 | enrollment_db (5436) | Đăng ký học, tiến độ, **job chấm điểm nguy cơ bỏ học (churn)** hằng đêm |
| `payment-service` | 8085 | payment_db (5437) | Thanh toán Stripe, webhook, chia doanh thu, hoàn tiền |
| `notification-service` | 8086 | — | Gửi email nhắc học khi học viên có nguy cơ bỏ học cao |
| `assessment-service` | 8087 | assessment_db (5439) | Quiz trắc nghiệm, chấm điểm tự động, lịch sử làm bài |
| `ai-service` (Python) | 8000 | ai_db + pgvector (5438) | RAG chatbot, tóm tắt bài học, dự đoán churn (scikit-learn) |

`common` là thư viện dùng chung (DTO `ApiResponse`, exception, event RabbitMQ, filter xác thực từ gateway).

### Tính năng AI — Student Churn Prediction

1. `enrollment-service` chạy job mỗi đêm (2:00, có **ShedLock** nên an toàn khi chạy nhiều replica), tính 8 feature cho
   từng học viên đang học: tiến độ, số ngày chưa học, % xem video tuần qua, thời gian hoàn thành bài gần nhất
   (enrollment-service), ngày đăng nhập & xu hướng đăng nhập (identity-service), số lần trượt quiz (assessment-service),
   số ticket hỗ trợ (user-service).
2. Gửi theo lô sang `ai-service` (`/api/v1/internal/ai/churn/predict-batch`), lưu `churn_score`, `churn_risk_level`.
3. Học viên rủi ro cao → event `churn.high_risk` → `notification-service` gửi email nhắc học (cooldown 7 ngày,
   tôn trọng cài đặt "Nhắc nhở học tập").
4. Instructor xem danh sách học viên theo mức rủi ro tại `/instructor/courses/{id}/students`.

## Yêu cầu

- Docker Desktop (Docker Compose v2)
- JDK 25 — chỉ cần khi build/chạy service trên máy thay vì trong Docker (đã có Maven Wrapper `./mvnw`)
- Node.js 20+ — để chạy các script seed dữ liệu
- Python 3.12 — chỉ cần khi chạy `ai-service` trên máy

## Cấu hình

1. **`docker/.env`** (dùng chung cho Compose) — tạo file với các biến:

   ```
   POSTGRES_USER=learnhub
   POSTGRES_PASSWORD=learnhub123
   REDIS_PASSWORD=
   RABBITMQ_USER=guest
   RABBITMQ_PASSWORD=guest
   JWT_SECRET=<chuỗi bí mật ≥ 256 bit, phải giống nhau ở gateway và identity-service>
   ```

2. **`.env` của từng service** — copy từ `.env.example` trong thư mục service và điền giá trị thật:

   | File | Cần điền |
   |---|---|
   | `identity-service/.env` | Cognito: `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`, `COGNITO_CLIENT_SECRET`, `COGNITO_REGION` |
   | `course-service/.env` | S3: `AWS_PROFILE` (AWS SSO), `AWS_REGION`, `AWS_S3_BUCKET` |
   | `payment-service/.env` | Stripe: `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` |
   | `ai-service/.env` | `GEMINI_API_KEY` (lấy tại aistudio.google.com/apikey), `LLM_PROVIDER` |
   | `notification-service/.env` | Không bắt buộc khi dev (email đi vào Mailpit); điền SMTP thật khi cần gửi email ra ngoài |

   Các file `.env` đã nằm trong `.gitignore` — **không commit secret**.

## Chạy bằng Docker Compose (khuyến nghị)

Chạy trong thư mục `docker/`:

```bash
# Toàn bộ: databases, Redis, RabbitMQ + tất cả service + Jaeger + Mailpit
docker compose -f docker-compose.apps.yml up -d --build

# Chỉ hạ tầng (DB, Redis, RabbitMQ) — khi muốn chạy service trên máy để debug
docker compose up -d

# Xem log / dừng
docker compose -f docker-compose.apps.yml logs -f enrollment-service
docker compose -f docker-compose.apps.yml down          # thêm -v để xóa luôn dữ liệu DB
```

Lần build đầu khá lâu vì Maven tải dependency bên trong Docker.

### Seed dữ liệu mẫu

```bash
# 1. Khóa học mẫu (đọc thư mục test_data_courses/ nằm cạnh thư mục backend/)
node docker/seed/seed-courses.js

# 2. Index nội dung bài học vào vector DB cho chatbot (tốn quota Gemini, chạy lại để tiếp tục)
docker compose -f docker/docker-compose.apps.yml exec ai-service python index_from_course_db.py

# 3. (Tùy chọn) 8 học viên demo với hành vi học khác nhau cho churn prediction
node docker/seed/seed-churn-demo.js            # thêm --remove để xóa
```

### Chạy job churn ngay (không chờ 2:00)

```bash
curl -X POST http://localhost:8084/api/v1/internal/churn/run
```

### Dữ liệu train cho churn (MLOps)

Mỗi lần job chạy, feature của từng enrollment được lưu vào bảng `churn_feature_snapshots` (enrollment_db). Sau 14 ngày, snapshot được gán nhãn `churn` (1 = không học bài nào trong 14 ngày và chưa hoàn thành khóa). Bật `CHURN_EXPORT_ENABLED=true` và đặt `CHURN_EXPORT_BUCKET` trong `enrollment-service/.env` để đẩy lên S3:

```
s3://<bucket>/churn/features/dt=YYYY-MM-DD/part-0.jsonl.gz   # ngay sau mỗi lần chạy
s3://<bucket>/churn/labels/dt=YYYY-MM-DD/part-0.jsonl.gz     # dt = ngày snapshot, ghi sau 14 ngày
```

Đẩy lại một ngày (ví dụ sau khi S3 lỗi):

```bash
curl -X POST "http://localhost:8084/api/v1/internal/churn/export?date=2026-10-03"
```

Model churn mặc định chạy ngay trong ai-service (file trong `churn_prediction/models`). Khi model đã được deploy lên SageMaker, đặt `CHURN_MODEL_BACKEND=sagemaker` và `CHURN_SAGEMAKER_ENDPOINT` trong `ai-service/.env`: ai-service chuyển tiếp request sang endpoint, enrollment-service không cần sửa gì. Định dạng request/response của endpoint và cách đóng gói nằm trong `churn_prediction/sagemaker_inference.py`.

## Chạy một service trên máy (dev)

```bash
# Hạ tầng chạy bằng Docker, service chạy bằng Maven
docker compose -f docker/docker-compose.yml up -d
./mvnw install -N -DskipTests && ./mvnw install -pl common -DskipTests
./mvnw -pl enrollment-service spring-boot:run
```

Mỗi service đọc `.env` trong thư mục của nó và mặc định kết nối `localhost` (port trong bảng trên).

`ai-service`:

```bash
cd ai-service
python -m venv venv && venv\Scripts\activate          # macOS/Linux: source venv/bin/activate
pip install -r requirements-dev.txt                    # requirements.txt nếu chỉ cần API
uvicorn app.main:app --reload --port 8000              # Swagger: http://localhost:8000/docs
python gradio_app.py                                    # giao diện demo: http://localhost:7860
python -m churn_prediction.train_model                  # train lại model churn
```

## Test

```bash
./mvnw test                                   # unit test
./mvnw verify -pl enrollment-service -am      # + integration test (Testcontainers, cần Docker)
```

## Công cụ khi chạy local

| Công cụ | URL |
|---|---|
| Swagger từng service | `http://localhost:<port>/swagger-ui.html` (ai-service: `/docs`) |
| RabbitMQ Management | http://localhost:15672 |
| Mailpit (xem email đã gửi) | http://localhost:8025 |
| Jaeger (tracing) | http://localhost:16686 |

## CI/CD

Mỗi service có workflow riêng trong `.github/workflows/` (`<service>-ci.yml`): build & test → build Docker image →
quét Trivy → push Docker Hub → cập nhật tag image trong repo `learnhub-infra` → ArgoCD tự deploy lên Kubernetes.

## Cấu trúc thư mục

```
backend/
├── common/                 # thư viện dùng chung
├── api-gateway/
├── identity-service/  user-service/  course-service/  enrollment-service/
├── payment-service/   assessment-service/  notification-service/
├── ai-service/             # Python: app/ (RAG chatbot), churn_prediction/ (model + MLOps scripts)
├── docker/                 # docker-compose.yml (hạ tầng), docker-compose.apps.yml (toàn bộ), seed/
└── .github/workflows/      # CI cho từng service
```
