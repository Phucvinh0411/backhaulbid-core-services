ALTER TABLE ekyc_verifications
    ADD COLUMN full_name VARCHAR(255);

ALTER TABLE companies
    ADD COLUMN ekyc_representative_name VARCHAR(255),
    ADD COLUMN representative_matched BOOLEAN,
    ADD COLUMN requires_authorization BOOLEAN DEFAULT FALSE,
    ADD COLUMN authorization_letter_url VARCHAR(500),
    ADD COLUMN authorization_letter_filename VARCHAR(255);
