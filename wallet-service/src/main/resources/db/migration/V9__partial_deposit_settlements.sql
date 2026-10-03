ALTER TABLE transactions
    ADD COLUMN idempotency_key VARCHAR(150),
    ADD COLUMN remaining_hold_amount NUMERIC(19, 2);

CREATE UNIQUE INDEX uk_transactions_idempotency_key
    ON transactions(idempotency_key)
    WHERE idempotency_key IS NOT NULL;
