CREATE TABLE transactions (
    id bigserial PRIMARY KEY,
    packet_hash varchar(64) NOT NULL,
    sender_vpa varchar(255) NOT NULL,
    receiver_vpa varchar(255) NOT NULL,
    amount numeric(19,2) NOT NULL,
    signed_at timestamptz NOT NULL,
    settled_at timestamptz NOT NULL,
    bridge_node_id varchar(255) NOT NULL,
    hop_count integer NOT NULL,
    status varchar(32) NOT NULL,
    CONSTRAINT transactions_packet_hash_unique UNIQUE (packet_hash),
    CONSTRAINT transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT transactions_hops_nonnegative CHECK (hop_count >= 0),
    CONSTRAINT transactions_distinct_accounts CHECK (sender_vpa <> receiver_vpa),
    CONSTRAINT transactions_status_valid CHECK (status IN ('SETTLED', 'REJECTED'))
);
