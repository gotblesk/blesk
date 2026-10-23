-- The server's own channel. Only `blesk-server official` sets this, so no space made through the API is ever official.
ALTER TABLE spaces ADD COLUMN official BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX spaces_one_official ON spaces (official) WHERE official;
