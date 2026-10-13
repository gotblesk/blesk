-- The SHA-256 of the token derived from the recovery phrase; the phrase and the token stay on the phone.
ALTER TABLE accounts ADD COLUMN recovery_hash BYTEA UNIQUE;
