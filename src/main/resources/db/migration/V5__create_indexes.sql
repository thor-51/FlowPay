-- transactions.transaction_reference, transactions.idempotency_key, and accounts.account_number
-- already have unique B-tree indexes created implicitly by their UNIQUE constraints in V2/V3,
-- so they are not repeated here.

CREATE INDEX idx_transactions_status ON transactions (status);
CREATE INDEX idx_transactions_created_at ON transactions (created_at DESC);
CREATE INDEX idx_transactions_user_id ON transactions (user_id);
CREATE INDEX idx_transactions_source_account_id ON transactions (source_account_id);
CREATE INDEX idx_transactions_destination_account_id ON transactions (destination_account_id);

CREATE INDEX idx_accounts_user_id ON accounts (user_id);

CREATE INDEX idx_audit_logs_transaction_id ON audit_logs (transaction_id);

-- Speeds up the RetryService sweep (status = RETRYING, ordered/filtered by updated_at).
CREATE INDEX idx_transactions_status_updated_at ON transactions (status, updated_at);
