CREATE TABLE complaints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id UUID REFERENCES trips(id) ON DELETE SET NULL,
    reporter_id UUID NOT NULL,
    respondent_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    decision VARCHAR(20),
    resolution VARCHAR(5000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_complaint_status CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'REJECTED')),
    CONSTRAINT chk_complaint_decision CHECK (decision IS NULL OR decision IN ('SHIPPER_WIN', 'CARRIER_WIN', 'BOTH'))
);

CREATE TABLE complaint_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id UUID NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    message VARCHAR(5000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_complaint_sender_role CHECK (sender_role IN ('SHIPPER', 'CARRIER', 'ADMIN'))
);

CREATE INDEX idx_complaints_created_at ON complaints(created_at DESC);
CREATE INDEX idx_complaints_parties ON complaints(reporter_id, respondent_id);
CREATE INDEX idx_complaint_messages_complaint_time ON complaint_messages(complaint_id, created_at ASC);
