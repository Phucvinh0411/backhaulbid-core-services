ALTER TABLE ekyc_verifications
    ADD COLUMN ocr_passed BOOLEAN,
    ADD COLUMN document_liveness_passed BOOLEAN,
    ADD COLUMN document_authenticity_passed BOOLEAN,
    ADD COLUMN face_matched BOOLEAN,
    ADD COLUMN failure_reason VARCHAR(500);
