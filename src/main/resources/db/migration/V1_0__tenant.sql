CREATE TABLE IF NOT EXISTS tenant (
                                      id UUID PRIMARY KEY,
                                      code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    tenant_key VARCHAR(128) NOT NULL,
    participant_did VARCHAR(255) NOT NULL,
    participant_bpn VARCHAR(64) NOT NULL,
    contact_name VARCHAR(255) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    contact_phone VARCHAR(32),
    k8s_namespace VARCHAR(255) NOT NULL,
    database_name VARCHAR(128) NOT NULL,
    database_schema VARCHAR(128),
    database_username VARCHAR(128) NOT NULL,
    management_api_base_url VARCHAR(512) NOT NULL,
    public_api_base_url VARCHAR(512) NOT NULL,
    control_plane_service VARCHAR(255),
    data_plane_service VARCHAR(255),
    lifecycle_status VARCHAR(32) NOT NULL DEFAULT 'PROVISIONING',
    last_error VARCHAR(2000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_tenant_code UNIQUE (code)
    );

CREATE INDEX IF NOT EXISTS idx_tenant_code ON tenant (code);
CREATE INDEX IF NOT EXISTS idx_tenant_enabled ON tenant (enabled);