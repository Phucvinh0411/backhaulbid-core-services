-- AES-GCM/Base64 expansion must support 255 Unicode characters, not only ASCII.
ALTER TABLE ekyc_verifications ALTER COLUMN full_name TYPE VARCHAR(2048);
-- Manual rollback: retain the wider column; narrowing may truncate existing ciphertext.
