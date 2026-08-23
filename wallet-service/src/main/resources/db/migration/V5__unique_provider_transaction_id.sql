-- A SePay transaction can settle at most one payment order.
-- Keep the index partial so all newly-created orders may remain NULL.
CREATE UNIQUE INDEX uq_payment_orders_provider_transaction_id
    ON payment_orders(provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
