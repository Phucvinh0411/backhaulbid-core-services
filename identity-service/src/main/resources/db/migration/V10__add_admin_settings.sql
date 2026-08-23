CREATE TABLE admin_settings (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope         VARCHAR(64) NOT NULL UNIQUE,
    settings_json TEXT NOT NULL,
    updated_by    UUID,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
