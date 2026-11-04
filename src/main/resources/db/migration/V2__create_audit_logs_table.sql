CREATE TABLE audit_logs (
    id UUID NOT NULL DEFAULT uuidv7(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_user_id UUID,
    actor_name VARCHAR(150) NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    actor_type VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    correlation_id VARCHAR(64),
    previous_state JSONB,
    new_state JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor_user FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_audit_logs_actor_type CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT ck_audit_logs_action CHECK (action IN (
        'CREATE',
        'UPDATE',
        'SETTLE',
        'PARTIAL_SETTLE_SPLIT',
        'CANCEL_SOFT_DELETE',
        'AUTO_OVERDUE_TRANSITION',
        'UPLOAD_ATTACHMENT'
    ))
);

CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_logs_occurred_at ON audit_logs (occurred_at DESC);
CREATE INDEX idx_audit_logs_actor_user_id ON audit_logs (actor_user_id);

CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Audit logs are strictly append-only and cannot be updated or deleted (RN01.2)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_audit_logs_mutation
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_modification();
