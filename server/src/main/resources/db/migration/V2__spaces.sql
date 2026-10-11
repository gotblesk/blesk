-- Public spaces are not end-to-end encrypted: titles and posts are stored in the clear on purpose.
CREATE TABLE spaces (
    id         BIGSERIAL PRIMARY KEY,
    handle     TEXT        NOT NULL UNIQUE,
    kind       TEXT        NOT NULL CHECK (kind IN ('channel', 'room')),
    title      TEXT        NOT NULL,
    about      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX spaces_handle_prefix ON spaces (handle text_pattern_ops);

CREATE TABLE space_channels (
    space_id BIGINT    NOT NULL REFERENCES spaces (id) ON DELETE CASCADE,
    id       TEXT      NOT NULL,
    title    TEXT      NOT NULL,
    position BIGSERIAL NOT NULL,
    PRIMARY KEY (space_id, id)
);

CREATE TABLE space_members (
    space_id   BIGINT      NOT NULL REFERENCES spaces (id) ON DELETE CASCADE,
    account_id BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    role       TEXT        NOT NULL CHECK (role IN ('owner', 'admin', 'member')),
    joined_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (space_id, account_id)
);

CREATE INDEX space_members_account ON space_members (account_id);

CREATE TABLE space_bans (
    space_id   BIGINT NOT NULL REFERENCES spaces (id) ON DELETE CASCADE,
    account_id BIGINT NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    PRIMARY KEY (space_id, account_id)
);

CREATE TABLE space_posts (
    id            BIGSERIAL PRIMARY KEY,
    space_id      BIGINT      NOT NULL,
    channel_id    TEXT        NOT NULL,
    author_id     BIGINT      NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    client_id     TEXT        NOT NULL,
    text          TEXT        NOT NULL,
    reply_to      BIGINT,
    -- The attachment pointer exactly as the author sent it, plus its id so the file is kept while the post exists.
    attachment    TEXT,
    attachment_id TEXT,
    created_at    TIMESTAMPTZ NOT NULL,
    edited_at     TIMESTAMPTZ,
    UNIQUE (author_id, client_id),
    FOREIGN KEY (space_id, channel_id) REFERENCES space_channels (space_id, id) ON DELETE CASCADE
);

CREATE INDEX space_posts_stream ON space_posts (space_id, channel_id, id DESC);
CREATE INDEX space_posts_attachment ON space_posts (attachment_id) WHERE attachment_id IS NOT NULL;
