CREATE TABLE audit_logs (
    id              UUID PRIMARY KEY,
    transaction_id  UUID,
    event_type      VARCHAR(50)              NOT NULL,
    message         VARCHAR(1000)            NOT NULL,
    metadata        TEXT,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT fk_audit_logs_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id)
);

COMMENT ON TABLE audit_logs IS 'Append-only record of transaction lifecycle events for traceability and debugging.';
