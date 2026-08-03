-- Local/demo reset only. Demo accounts may already exist with generated UUIDs,
-- so resolve them by their stable demo phone numbers instead of hard-coded ids.
DELETE FROM ekyc_verifications e
USING accounts a
WHERE e.account_id = a.id
  AND a.phone IN ('0900000002', '0900000003');
