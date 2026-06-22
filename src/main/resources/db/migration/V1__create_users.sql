CREATE TABLE users (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255)            NOT NULL,
    email           VARCHAR(255)            NOT NULL,
    password_hash   VARCHAR(255)            NOT NULL,
    role            VARCHAR(20)             NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);

COMMENT ON TABLE users IS 'Application users who own accounts and initiate transactions.';
