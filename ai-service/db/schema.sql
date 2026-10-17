-- =========================================================
-- Schema cho AI Service (RAG Chatbot)
-- Khớp với thiết kế trong Backend Blueprint.md / PDF tổng quan
-- =========================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- Lưu các đoạn (chunk) nội dung bài học đã được embed
CREATE TABLE IF NOT EXISTS lesson_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id       UUID NOT NULL,
    course_id       UUID NOT NULL,
    chunk_index     INT NOT NULL,
    chunk_text      TEXT NOT NULL,
    embedding       vector(3072) NOT NULL,   -- Gemini gemini-embedding-001 = 3072 chiều (mặc định)
    token_count     INT,
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE(lesson_id, chunk_index)
);

-- KHÔNG tạo index ivfflat cho cột embedding: pgvector giới hạn ivfflat/hnsw
-- chỉ hỗ trợ tối đa 2000 chiều, trong khi gemini-embedding-001 cho ra vector
-- 3072 chiều. Với quy mô dữ liệu của 1 khóa luận (vài trăm chunk), full scan
-- (không index) vẫn đủ nhanh — chỉ cần cân nhắc thêm index khi dữ liệu lên
-- tới hàng chục nghìn chunk trở lên.
CREATE INDEX IF NOT EXISTS idx_lesson_embeddings_course
    ON lesson_embeddings (course_id);

-- Lưu lịch sử phiên chat của từng học viên trong từng khóa học
CREATE TABLE IF NOT EXISTS chatbot_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    course_id       UUID NOT NULL,
    created_at      TIMESTAMP DEFAULT NOW(),
    last_message_at TIMESTAMP DEFAULT NOW()
);

-- Lưu từng tin nhắn (cả câu hỏi lẫn câu trả lời) để phục vụ báo cáo/demo
CREATE TABLE IF NOT EXISTS chatbot_messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID REFERENCES chatbot_sessions(id) ON DELETE CASCADE,
    role                VARCHAR(20) NOT NULL,   -- 'user' | 'assistant'
    content             TEXT NOT NULL,
    retrieved_chunks    JSONB,                  -- lưu lại các chunk đã dùng để trả lời (để demo "có nguồn")
    model_used          VARCHAR(100),
    tokens_used         INT,
    created_at          TIMESTAMP DEFAULT NOW()
);
