-- enrollments
CREATE INDEX idx_enrollments_user_id ON enrollments (user_id);
CREATE INDEX idx_enrollments_course_id ON enrollments (course_id);
CREATE INDEX idx_enrollments_user_completed ON enrollments (user_id, is_completed);

-- lesson_progress
CREATE INDEX idx_lesson_progress_enrollment_id ON lesson_progress (enrollment_id);
CREATE INDEX idx_lesson_progress_user_id ON lesson_progress (user_id);
CREATE INDEX idx_lesson_progress_lesson_id ON lesson_progress (lesson_id);