-- Existing signature evidence remains readable; new evidence identifies the exact signing key.
ALTER TABLE payment_signatures ADD COLUMN key_fingerprint varchar(64);
