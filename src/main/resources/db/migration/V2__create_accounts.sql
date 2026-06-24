CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    user_id         UUID                     NOT NULL,
    account_number  VARCHAR(34)              NOT NULL,
    balance         NUMERIC(19, 4)           NOT NULL DEFAULT 0,
    currency        VARCHAR(3)                  NOT NULL,
    version         BIGINT                   NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_accounts_account_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)
);

COMMENT ON TABLE accounts IS 'Holds a monetary balance for a user; version column backs JPA optimistic locking.';
COMMENT ON COLUMN accounts.version IS 'Optimistic lock token incremented by Hibernate on every update.';
