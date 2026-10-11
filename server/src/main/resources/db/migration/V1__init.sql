CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    username        TEXT        NOT NULL UNIQUE,
    identity_key    BYTEA       NOT NULL,
    registration_id INTEGER     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE auth_challenges (
    nonce      TEXT PRIMARY KEY,
    username   TEXT        NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX auth_challenges_expiry ON auth_challenges (expires_at);

CREATE TABLE auth_tokens (
    token_hash BYTEA PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX auth_tokens_account ON auth_tokens (account_id);
CREATE INDEX auth_tokens_expiry ON auth_tokens (expires_at);

CREATE TABLE signed_prekeys (
    account_id BIGINT PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    key_id     INTEGER NOT NULL,
    public_key BYTEA   NOT NULL,
    signature  BYTEA   NOT NULL
);

CREATE TABLE kyber_last_resort_keys (
    account_id BIGINT PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    key_id     INTEGER NOT NULL,
    public_key BYTEA   NOT NULL,
    signature  BYTEA   NOT NULL
);

CREATE TABLE one_time_prekeys (
    account_id BIGINT  NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    key_id     INTEGER NOT NULL,
    public_key BYTEA   NOT NULL,
    PRIMARY KEY (account_id, key_id)
);

CREATE TABLE kyber_prekeys (
    account_id BIGINT  NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    key_id     INTEGER NOT NULL,
    public_key BYTEA   NOT NULL,
    signature  BYTEA   NOT NULL,
    PRIMARY KEY (account_id, key_id)
);

-- Server ids come from one sequence so ephemeral messages, which are never stored, still get unique ids.
CREATE SEQUENCE message_ids;

CREATE TABLE queued_messages (
    id           BIGINT PRIMARY KEY,
    recipient_id BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    sender_id    BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    type         INTEGER     NOT NULL,
    body         BYTEA       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX queued_messages_recipient ON queued_messages (recipient_id, id);
CREATE INDEX queued_messages_created ON queued_messages (created_at);

-- Remembers what a sender already sent, without the recipient, so retries with the same client id are no-ops
-- even after the message itself has been acknowledged and deleted.
CREATE TABLE sent_messages (
    sender_id BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    client_id TEXT        NOT NULL,
    server_id BIGINT      NOT NULL,
    sent_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (sender_id, client_id)
);

CREATE INDEX sent_messages_sent_at ON sent_messages (sent_at);

CREATE TABLE attachments (
    id         TEXT PRIMARY KEY,
    size       BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX attachments_created ON attachments (created_at);
