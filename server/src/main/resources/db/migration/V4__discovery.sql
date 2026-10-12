-- Whether anyone may find the account by typing its username. Hidden accounts are still reached
-- through their link or QR code, which carries a key derived from the public identity key.
ALTER TABLE accounts ADD COLUMN discoverable BOOLEAN NOT NULL DEFAULT TRUE;
