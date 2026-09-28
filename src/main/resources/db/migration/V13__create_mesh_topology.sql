CREATE TABLE mesh_connections (
    id bigserial PRIMARY KEY,
    source_device_id bigint NOT NULL REFERENCES devices(id),
    target_device_id bigint NOT NULL REFERENCES devices(id),
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    link_type varchar(32) NOT NULL DEFAULT 'BLUETOOTH',
    created_at timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz,
    CONSTRAINT mesh_connections_distinct CHECK (source_device_id<>target_device_id),
    CONSTRAINT mesh_connections_status_valid CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT mesh_connections_pair_unique UNIQUE(source_device_id,target_device_id)
);
CREATE INDEX idx_mesh_connections_source_status ON mesh_connections(source_device_id,status);
CREATE INDEX idx_mesh_connections_target_status ON mesh_connections(target_device_id,status);

CREATE TABLE packet_routes (
    id bigserial PRIMARY KEY,
    packet_id varchar(36) NOT NULL,
    payment_id bigint REFERENCES payments(id),
    source_device_id bigint NOT NULL REFERENCES devices(id),
    destination_device_id bigint NOT NULL REFERENCES devices(id),
    hop_number integer NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    forwarded_at timestamptz,
    ttl_after_hop integer NOT NULL,
    CONSTRAINT packet_routes_hop_nonnegative CHECK (hop_number>=0),
    CONSTRAINT packet_routes_ttl_nonnegative CHECK (ttl_after_hop>=0),
    CONSTRAINT packet_routes_one_arrival UNIQUE(packet_id,destination_device_id)
);
CREATE INDEX idx_packet_routes_packet_id ON packet_routes(packet_id);
CREATE INDEX idx_packet_routes_payment_id ON packet_routes(payment_id);
CREATE INDEX idx_packet_routes_source ON packet_routes(source_device_id);
CREATE INDEX idx_packet_routes_destination ON packet_routes(destination_device_id);
