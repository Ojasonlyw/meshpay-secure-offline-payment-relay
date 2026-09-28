CREATE UNIQUE INDEX idx_payments_nonce_unique ON payments(nonce) WHERE nonce IS NOT NULL;

CREATE TABLE payment_signatures (
    id bigserial PRIMARY KEY,
    payment_id bigint NOT NULL UNIQUE REFERENCES payments(id),
    device_id bigint REFERENCES devices(id),
    signature text,
    signature_algorithm varchar(32),
    canonical_payload_hash varchar(64) NOT NULL,
    verified_at timestamptz NOT NULL DEFAULT now(),
    verification_status varchar(32) NOT NULL,
    failure_code varchar(64),
    CONSTRAINT payment_signatures_status_valid CHECK (verification_status IN ('VERIFIED','FAILED'))
);
CREATE INDEX idx_payment_signatures_device_id ON payment_signatures(device_id);
CREATE INDEX idx_payment_signatures_status ON payment_signatures(verification_status);
