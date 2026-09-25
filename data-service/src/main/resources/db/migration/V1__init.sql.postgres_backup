-- =============================================================================
-- V1__init.sql: Initial PostgreSQL Schema for SIH Cybercrime Investigation System
-- =============================================================================

-- Ensure UUID extension is available
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- 1. Banks
-- -----------------------------------------------------------------------------
CREATE TABLE banks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 2. Jurisdictions (Hierarchy: State -> District -> Station)
-- -----------------------------------------------------------------------------
CREATE TABLE jurisdictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID REFERENCES jurisdictions(id) ON DELETE RESTRICT,
    level VARCHAR(20) NOT NULL CHECK (level IN ('STATE', 'DISTRICT', 'STATION')),
    name VARCHAR(255) NOT NULL,
    path VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_jurisdictions_path ON jurisdictions (path varchar_pattern_ops);

-- -----------------------------------------------------------------------------
-- 3. Users & Scope
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(30) NOT NULL CHECK (role IN ('COMPLAINANT', 'BANK_EMPLOYEE', 'BANK_MANAGER', 'POLICE', 'CYBER_OFFICER', 'ADMIN')),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone_encrypted VARCHAR(512),
    phone_hash VARCHAR(64),
    oauth_provider VARCHAR(50),
    oauth_subject VARCHAR(255),
    employee_id VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    bank_id UUID REFERENCES banks(id) ON DELETE RESTRICT,
    jurisdiction_id UUID REFERENCES jurisdictions(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DEACTIVATED')),
    token_version INT NOT NULL DEFAULT 1,
    last_roster_sync TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_role_scope CHECK (
        (role IN ('BANK_EMPLOYEE', 'BANK_MANAGER') AND bank_id IS NOT NULL) OR
        (role = 'POLICE' AND jurisdiction_id IS NOT NULL) OR
        (role IN ('COMPLAINANT', 'CYBER_OFFICER', 'ADMIN'))
    )
);

CREATE INDEX idx_users_phone_hash ON users (phone_hash);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_bank_id ON users (bank_id);
CREATE INDEX idx_users_jurisdiction_id ON users (jurisdiction_id);

-- -----------------------------------------------------------------------------
-- 4. Entities (Bank Accounts, UPI IDs, Wallets, Phones)
-- -----------------------------------------------------------------------------
CREATE TABLE entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_hash VARCHAR(64) NOT NULL UNIQUE,
    account_encrypted VARCHAR(512) NOT NULL,
    bank_id UUID REFERENCES banks(id) ON DELETE RESTRICT,
    type VARCHAR(20) NOT NULL CHECK (type IN ('ACCOUNT', 'UPI', 'WALLET', 'PHONE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_entities_bank_id ON entities (bank_id);

-- -----------------------------------------------------------------------------
-- 5. Complaints (One complaint = One incident)
-- -----------------------------------------------------------------------------
CREATE TABLE complaints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    human_reference VARCHAR(50) NOT NULL UNIQUE,
    complainant_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    jurisdiction_id UUID REFERENCES jurisdictions(id) ON DELETE RESTRICT,
    fraud_type VARCHAR(50) NOT NULL,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    incident_time TIMESTAMPTZ NOT NULL,
    description_original TEXT NOT NULL,
    description_language VARCHAR(10) NOT NULL DEFAULT 'en',
    description_english TEXT,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('WEB', 'WHATSAPP')),
    status VARCHAR(30) NOT NULL DEFAULT 'FILED' CHECK (status IN (
        'FILED', 'TRIAGED', 'ASSIGNED', 'UNDER_INVESTIGATION', 
        'FREEZE_REQUESTED', 'BANK_RESPONDED', 'CLOSED_FRAUD', 'CLOSED_NOT_FRAUD'
    )),
    assigned_officer_id UUID REFERENCES users(id) ON DELETE SET NULL,
    label VARCHAR(30) CHECK (label IN ('FRAUD', 'NOT_FRAUD')),
    labeled_by UUID REFERENCES users(id) ON DELETE SET NULL,
    labeled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_complaint_label_status CHECK (
        (status = 'CLOSED_FRAUD' AND label = 'FRAUD' AND labeled_by IS NOT NULL AND labeled_at IS NOT NULL) OR
        (status = 'CLOSED_NOT_FRAUD' AND label = 'NOT_FRAUD' AND labeled_by IS NOT NULL AND labeled_at IS NOT NULL) OR
        (status NOT IN ('CLOSED_FRAUD', 'CLOSED_NOT_FRAUD') AND label IS NULL)
    )
);

CREATE INDEX idx_complaints_complainant_id ON complaints (complainant_id);
CREATE INDEX idx_complaints_jurisdiction_id ON complaints (jurisdiction_id);
CREATE INDEX idx_complaints_status_created ON complaints (status, created_at);
CREATE INDEX idx_complaints_assigned_officer ON complaints (assigned_officer_id);

-- -----------------------------------------------------------------------------
-- 6. Complaint Accounts Mapping
-- -----------------------------------------------------------------------------
CREATE TABLE complaint_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id UUID NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE RESTRICT,
    role VARCHAR(20) NOT NULL CHECK (role IN ('SENDER', 'RECEIVER', 'SUSPECT')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_complaint_entity_role UNIQUE (complaint_id, entity_id, role)
);

CREATE INDEX idx_complaint_accounts_entity ON complaint_accounts (entity_id);

-- -----------------------------------------------------------------------------
-- 7. Case Events (Append-only state machine history)
-- -----------------------------------------------------------------------------
CREATE TABLE case_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id UUID NOT NULL REFERENCES complaints(id) ON DELETE RESTRICT,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_case_events_complaint_time ON case_events (complaint_id, created_at);

-- -----------------------------------------------------------------------------
-- 8. Evidence Files
-- -----------------------------------------------------------------------------
CREATE TABLE evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id UUID NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    file_path VARCHAR(1024) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 VARCHAR(64) NOT NULL,
    uploader_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_evidence_complaint_id ON evidence (complaint_id);

-- -----------------------------------------------------------------------------
-- 9. Bank Uploads
-- -----------------------------------------------------------------------------
CREATE TABLE bank_uploads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_id UUID NOT NULL REFERENCES banks(id) ON DELETE RESTRICT,
    uploader_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    file_path VARCHAR(1024) NOT NULL,
    file_checksum VARCHAR(64) NOT NULL UNIQUE,
    row_count INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewer_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    review_note TEXT,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_bank_upload_reviewer_not_uploader CHECK (uploader_id <> reviewer_id),
    CONSTRAINT chk_bank_upload_review_status CHECK (
        (status = 'PENDING' AND reviewer_id IS NULL AND reviewed_at IS NULL) OR
        (status IN ('APPROVED', 'REJECTED') AND reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL)
    )
);

CREATE INDEX idx_bank_uploads_bank_status ON bank_uploads (bank_id, status);

-- -----------------------------------------------------------------------------
-- 10. Transactions
-- -----------------------------------------------------------------------------
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utr VARCHAR(100) NOT NULL UNIQUE,
    sender_id UUID NOT NULL REFERENCES entities(id) ON DELETE RESTRICT,
    receiver_id UUID NOT NULL REFERENCES entities(id) ON DELETE RESTRICT,
    amount NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    timestamp TIMESTAMPTZ NOT NULL,
    source VARCHAR(20) NOT NULL CHECK (source IN ('COMPLAINT', 'BANK_UPLOAD', 'DATASET', 'STREAM')),
    complaint_id UUID REFERENCES complaints(id) ON DELETE SET NULL,
    upload_id UUID REFERENCES bank_uploads(id) ON DELETE SET NULL,
    ground_truth_label VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_sender_time ON transactions (sender_id, timestamp);
CREATE INDEX idx_transactions_receiver_time ON transactions (receiver_id, timestamp);
CREATE INDEX idx_transactions_utr ON transactions (utr);
CREATE INDEX idx_transactions_time ON transactions (timestamp);

-- -----------------------------------------------------------------------------
-- 11. Freeze & Info Requests (7-Day Resolution SLA)
-- -----------------------------------------------------------------------------
CREATE TABLE freeze_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id UUID NOT NULL REFERENCES complaints(id) ON DELETE RESTRICT,
    entity_id UUID NOT NULL REFERENCES entities(id) ON DELETE RESTRICT,
    bank_id UUID NOT NULL REFERENCES banks(id) ON DELETE RESTRICT,
    raised_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    raised_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN' CHECK (status IN (
        'OPEN', 'FROZEN', 'PARTIALLY_FROZEN', 'REJECTED', 'INFO_PROVIDED'
    )),
    response_note TEXT,
    responded_by UUID REFERENCES users(id) ON DELETE SET NULL,
    responded_at TIMESTAMPTZ,
    reminder_sent_at TIMESTAMPTZ,
    escalated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_freeze_requests_status_due ON freeze_requests (status, due_at);
CREATE INDEX idx_freeze_requests_complaint ON freeze_requests (complaint_id);
CREATE INDEX idx_freeze_requests_bank ON freeze_requests (bank_id);

-- -----------------------------------------------------------------------------
-- 12. Training Snapshots (Immutable de-identified exports)
-- -----------------------------------------------------------------------------
CREATE TABLE training_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version INT NOT NULL UNIQUE,
    creator_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    case_count INT NOT NULL DEFAULT 0,
    fraud_count INT NOT NULL DEFAULT 0,
    storage_uri VARCHAR(1024) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 13. Model Versions & Registry
-- -----------------------------------------------------------------------------
CREATE TABLE model_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL CHECK (name IN ('graphsage', 'xgboost')),
    version VARCHAR(50) NOT NULL,
    snapshot_id UUID REFERENCES training_snapshots(id) ON DELETE RESTRICT,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'CANDIDATE' CHECK (status IN ('CANDIDATE', 'ACTIVE', 'RETIRED')),
    promoted_by UUID REFERENCES users(id) ON DELETE SET NULL,
    promoted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_model_versions_unique_active ON model_versions (name) WHERE status = 'ACTIVE';

-- -----------------------------------------------------------------------------
-- 14. Predictions
-- -----------------------------------------------------------------------------
CREATE TABLE predictions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id UUID NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    model_version_id UUID REFERENCES model_versions(id) ON DELETE SET NULL,
    risk NUMERIC(5, 4) NOT NULL CHECK (risk >= 0 AND risk <= 1),
    confidence NUMERIC(5, 4) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    explanation JSONB NOT NULL DEFAULT '{}'::jsonb,
    model_available BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_predictions_complaint_time ON predictions (complaint_id, created_at);

-- -----------------------------------------------------------------------------
-- 15. Audit Log (Append-only)
-- -----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    role VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id VARCHAR(100),
    detail JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_action_time ON audit_log (action, created_at);

-- -----------------------------------------------------------------------------
-- 16. WhatsApp Sessions
-- -----------------------------------------------------------------------------
CREATE TABLE whatsapp_sessions (
    phone_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    state VARCHAR(30) NOT NULL DEFAULT 'LANGUAGE' CHECK (state IN (
        'LANGUAGE', 'IDENTITY_LINK', 'FRAUD_TYPE', 'AMOUNT_DATE', 
        'ACCOUNT_OR_UTR', 'DESCRIPTION', 'EVIDENCE', 'CONFIRM', 'DONE'
    )),
    language VARCHAR(10) NOT NULL DEFAULT 'en',
    draft JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- 17. Notifications
-- -----------------------------------------------------------------------------
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(20) NOT NULL CHECK (channel IN ('WHATSAPP', 'SMS', 'EMAIL')),
    template VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'SENT', 'FAILED')),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_notifications_status_time ON notifications (status, created_at);

-- -----------------------------------------------------------------------------
-- 18. Append-only Triggers on audit_log and case_events
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION prevent_update_or_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only. UPDATE and DELETE operations are strictly prohibited.', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
BEFORE UPDATE OR DELETE ON audit_log
FOR EACH ROW EXECUTE FUNCTION prevent_update_or_delete();

CREATE TRIGGER trg_case_events_append_only
BEFORE UPDATE OR DELETE ON case_events
FOR EACH ROW EXECUTE FUNCTION prevent_update_or_delete();
