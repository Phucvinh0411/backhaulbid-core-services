-- Local/demo reset only. Keep demo accounts unverified so the eKYC flow
-- can be exercised from the UI without carrying stale verification data.
DELETE FROM ekyc_verifications
WHERE account_id IN (
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333'
);
