ALTER TABLE companies
    ADD COLUMN legal_representative VARCHAR(255),
    ADD COLUMN business_license_filename VARCHAR(255),
    ADD COLUMN rejection_reason VARCHAR(500);
