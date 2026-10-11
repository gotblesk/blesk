-- Bots are not end-to-end encrypted: what people write to them and what they answer is stored in the clear on purpose.
-- Bot usernames end with "_bot" and people may not register such names, so the two namespaces never overlap.
CREATE TABLE bots (
    id             BIGSERIAL PRIMARY KEY,
    username       TEXT        NOT NULL UNIQUE,
    owner_id       BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    name           TEXT        NOT NULL,
    about          TEXT        NOT NULL,
    -- SHA-256 of the token; the token itself is shown to the owner once and never stored.
    token_hash     BYTEA       NOT NULL UNIQUE,
    commands       TEXT        NOT NULL DEFAULT '[]',
    webhook_url    TEXT,
    webhook_secret TEXT,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX bots_owner ON bots (owner_id);

-- People who wrote to a bot. A bot may only message them, so a token cannot be used to spam strangers.
CREATE TABLE bot_chats (
    bot_id     BIGINT      NOT NULL REFERENCES bots (id) ON DELETE CASCADE,
    account_id BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (bot_id, account_id)
);

-- What people sent to a bot, waiting for getUpdates or a webhook. Acknowledged rows stay a while so retries stay no-ops.
CREATE TABLE bot_updates (
    id          BIGSERIAL PRIMARY KEY,
    bot_id      BIGINT      NOT NULL REFERENCES bots (id) ON DELETE CASCADE,
    from_id     BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    client_id   TEXT        NOT NULL,
    callback_id TEXT UNIQUE,
    answered    BOOLEAN     NOT NULL DEFAULT FALSE,
    payload     TEXT        NOT NULL,
    consumed    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (bot_id, from_id, client_id)
);

CREATE INDEX bot_updates_pending ON bot_updates (bot_id, id) WHERE NOT consumed;
CREATE INDEX bot_updates_created ON bot_updates (created_at);

-- Messages a bot sent, so edits and button presses can be checked against the right chat.
CREATE TABLE bot_messages (
    bot_id     BIGINT      NOT NULL REFERENCES bots (id) ON DELETE CASCADE,
    message_id TEXT        NOT NULL,
    chat_id    BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (bot_id, message_id)
);

CREATE INDEX bot_messages_created ON bot_messages (created_at);

-- A queued envelope comes either from a person or from a bot.
ALTER TABLE queued_messages ALTER COLUMN sender_id DROP NOT NULL;
ALTER TABLE queued_messages ADD COLUMN sender_bot_id BIGINT REFERENCES bots (id) ON DELETE CASCADE;
ALTER TABLE queued_messages ADD CONSTRAINT queued_messages_one_sender CHECK ((sender_id IS NULL) <> (sender_bot_id IS NULL));
