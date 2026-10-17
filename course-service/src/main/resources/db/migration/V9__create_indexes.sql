-- courses
CREATE INDEX idx_courses_instructor_id  ON courses (instructor_id);
CREATE INDEX idx_courses_category_id    ON courses (category_id);
CREATE INDEX idx_courses_status         ON courses (status);
CREATE INDEX idx_courses_slug           ON courses (slug);
CREATE INDEX idx_courses_is_featured    ON courses (is_featured) WHERE is_featured = true;
CREATE INDEX idx_courses_is_bestseller  ON courses (is_bestseller) WHERE is_bestseller = true;

-- Full-text search index (used in Part 3)
CREATE INDEX idx_courses_fts ON courses
    USING GIN (to_tsvector('simple', title || ' ' || COALESCE(short_description, '')));

-- sections
CREATE INDEX idx_sections_course_id ON sections (course_id);

-- lessons
CREATE INDEX idx_lessons_section_id ON lessons (section_id);
CREATE INDEX idx_lessons_course_id  ON lessons (course_id);

-- reviews
CREATE INDEX idx_reviews_course_id  ON reviews (course_id);
CREATE INDEX idx_reviews_user_id    ON reviews (user_id);

-- qa_questions
CREATE INDEX idx_qa_questions_lesson_id ON qa_questions (lesson_id);
CREATE INDEX idx_qa_questions_course_id ON qa_questions (course_id);
CREATE INDEX idx_qa_questions_user_id   ON qa_questions (user_id);

-- qa_answers
CREATE INDEX idx_qa_answers_question_id ON qa_answers (question_id);

-- lesson_notes
CREATE INDEX idx_lesson_notes_lesson_id ON lesson_notes (lesson_id);
CREATE INDEX idx_lesson_notes_user_id   ON lesson_notes (user_id);

-- coupons
CREATE INDEX idx_coupons_code       ON coupons (code);
CREATE INDEX idx_coupons_course_id  ON coupons (course_id);
CREATE INDEX idx_coupons_is_active  ON coupons (is_active) WHERE is_active = true;