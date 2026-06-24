CREATE TABLE transactions (
    id                      UUID PRIMARY KEY,
    transaction_reference   VARCHAR(64)              NOT NULL,
    user_id                 UUID                     NOT NULL,
    source_account_id       UUID                     NOT NULL,
    destination_account_id  UUID                     NOT NULL,
    amount                  NUMERIC(19, 4)           NOT NULL,
    currency                VARCHAR(3)                  NOT NULL,
    status                  VARCHAR(20)              NOT NULL,
    failure_reason          VARCHAR(500),
    idempotency_key         VARCHAR(128),
    retry_count             INTEGER                  NOT NULL DEFAULT 0,
    version                 BIGINT                   NOT NULL DEFAULT 0,
    processed_at            TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_transactions_reference UNIQUE (transaction_reference),
    CONSTRAINT uq_transactions_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_source_account FOREIGN KEY (source_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_destination_account FOREIGN KEY (destination_account_id) REFERENCES accounts (id),
    CONSTRAINT chk_transactions_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'RETRYING', 'DEAD_LETTERED')),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_accounts_differ CHECK (source_account_id <> destination_account_id)
);

COMMENT ON TABLE transactions IS 'A single transfer between two accounts, processed asynchronously via Kafka.';
COMMENT ON COLUMN transactions.idempotency_key IS 'Client-supplied Idempotency-Key header value; unique when present, NULL allowed since Postgres treats multiple NULLs as distinct under a UNIQUE constraint.';
