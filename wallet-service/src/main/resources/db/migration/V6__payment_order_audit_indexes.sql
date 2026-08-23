CREATE INDEX idx_payment_orders_status_created
    ON payment_orders(status, created_at DESC);
