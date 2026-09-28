CREATE TABLE payments (
    id bigserial PRIMARY KEY,
    payment_id uuid NOT NULL UNIQUE,
    packet_hash varchar(64) NOT NULL UNIQUE,
    sender_vpa varchar(255) NOT NULL,
    receiver_vpa varchar(255) NOT NULL,
    amount numeric(19,2) NOT NULL,
    status varchar(32) NOT NULL,
    nonce uuid,
    signed_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL DEFAULT current_timestamp,
    settled_at timestamptz,
    failure_code varchar(64),
    failure_message varchar(255),
    bridge_node_id varchar(255) NOT NULL,
    hop_count integer NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT payments_amount_positive CHECK (amount > 0),
    CONSTRAINT payments_distinct_accounts CHECK (sender_vpa <> receiver_vpa),
    CONSTRAINT payments_hops_nonnegative CHECK (hop_count >= 0),
    CONSTRAINT payments_status_valid CHECK (status IN ('PENDING','PROCESSING','SETTLED','REJECTED','FAILED','EXPIRED'))
);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_sender ON payments(sender_vpa);
CREATE INDEX idx_payments_receiver ON payments(receiver_vpa);
CREATE INDEX idx_payments_received ON payments(received_at);
CREATE INDEX idx_payments_settled ON payments(settled_at);

-- Historical instructions did not retain their nonce. Do not invent one.
INSERT INTO payments(payment_id,packet_hash,sender_vpa,receiver_vpa,amount,status,
                     signed_at,received_at,settled_at,bridge_node_id,hop_count)
SELECT md5('upi-mesh:legacy:' || packet_hash)::uuid,packet_hash,sender_vpa,receiver_vpa,
       amount,status,signed_at,settled_at,
       CASE WHEN status='SETTLED' THEN settled_at END,bridge_node_id,hop_count
FROM transactions;
