-- Registration by invite. An invite without an owner was made on the server itself for its owner
-- and never expires. Whether it is used is told by used_at: used_by is forgotten with the account.
CREATE TABLE invites (
    code       TEXT PRIMARY KEY,
    owner_id   BIGINT REFERENCES accounts (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    used_by    BIGINT REFERENCES accounts (id) ON DELETE SET NULL,
    used_at    TIMESTAMPTZ
);

CREATE INDEX invites_owner ON invites (owner_id, created_at DESC);
CREATE INDEX invites_used_by ON invites (used_by) WHERE used_by IS NOT NULL;
CREATE INDEX invites_used_at ON invites (used_at) WHERE used_at IS NOT NULL;
CREATE INDEX invites_unused_expiry ON invites (expires_at) WHERE used_at IS NULL;

-- One-off events of this server. The owner's first invites are made once: used ones are deleted
-- after a while, and the command that prints them must not make a new batch on its own then.
CREATE TABLE server_events (
    name        TEXT PRIMARY KEY,
    happened_at TIMESTAMPTZ NOT NULL
);
