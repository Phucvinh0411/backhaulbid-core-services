-- R4: private media references are stored as object keys (folder/uuid.ext), never as storage URLs.
-- Existing rows keep their data: a stored URL is reduced to its key and no row is deleted or reordered.
-- Responses already expose proxy paths; the key is what the service uses to read the private object.

ALTER TABLE trip_handover_photos RENAME COLUMN url TO object_key;
UPDATE trip_handover_photos
   SET object_key = regexp_replace(object_key,
       '^https?://.*/(handover-photos/[0-9a-fA-F-]{36}\.(png|jpe?g|webp|gif))$', '\1')
 WHERE object_key ~* '^https?://';

ALTER TABLE insurance_claim_evidence RENAME COLUMN file_url TO object_key;
UPDATE insurance_claim_evidence
   SET object_key = regexp_replace(object_key,
       '^https?://.*/(claim-evidence/[0-9a-fA-F-]{36}\.(png|jpe?g|webp|gif|pdf))$', '\1')
 WHERE object_key ~* '^https?://';
