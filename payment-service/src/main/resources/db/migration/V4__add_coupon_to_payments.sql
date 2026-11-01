ALTER TABLE payments
    ADD COLUMN coupon_code     VARCHAR(50),
    ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN payments.coupon_code     IS 'Coupon applied to this payment, counted as used only after the payment succeeds';
COMMENT ON COLUMN payments.discount_amount IS 'Already subtracted from amount, so the pre-discount price is amount + discount_amount';
