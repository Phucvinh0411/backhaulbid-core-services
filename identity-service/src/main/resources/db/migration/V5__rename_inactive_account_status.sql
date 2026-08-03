ALTER TABLE accounts DROP CONSTRAINT chk_accounts_status;

UPDATE accounts
SET status = 'INACTIVE'
WHERE status = 'IN_ACTIVE';

ALTER TABLE accounts ALTER COLUMN status SET DEFAULT 'INACTIVE';

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_status
        CHECK (status IN ('INACTIVE', 'ACTIVE', 'BLOCKED'));
