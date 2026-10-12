-- Large attachments arrive in chunks, each stored as its own object; a single-request upload keeps chunks = 0.
ALTER TABLE attachments ADD COLUMN chunks INTEGER NOT NULL DEFAULT 0;
ALTER TABLE attachments ADD COLUMN complete BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE attachments ADD COLUMN owner_id BIGINT REFERENCES accounts (id) ON DELETE CASCADE;

CREATE TABLE attachment_chunks (
    attachment_id TEXT    NOT NULL REFERENCES attachments (id) ON DELETE CASCADE,
    n             INTEGER NOT NULL,
    PRIMARY KEY (attachment_id, n)
);
