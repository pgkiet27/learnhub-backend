-- payments
CREATE INDEX idx_payments_user_id ON payments (user_id);
CREATE INDEX idx_payments_course_id ON payments (course_id);
CREATE INDEX idx_payments_user_course_status ON payments (user_id, course_id, status);
CREATE INDEX idx_payments_status ON payments (status);

-- refunds
CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);