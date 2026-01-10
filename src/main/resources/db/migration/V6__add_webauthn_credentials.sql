CREATE TABLE IF NOT EXISTS webauthn_credentials
(
    id BIGSERIAL PRIMARY KEY,
    username         VARCHAR(255) NOT NULL,
    credential_id BYTEA NOT NULL UNIQUE,
    public_key_cose BYTEA NOT NULL,
    sign_count       BIGINT       NOT NULL DEFAULT 0,
    transports       VARCHAR(255),
    attestation_type VARCHAR(64),
    aaguid           VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_webauthn_credentials_username ON webauthn_credentials(username);

