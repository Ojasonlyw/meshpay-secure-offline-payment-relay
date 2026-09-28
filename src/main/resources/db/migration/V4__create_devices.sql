CREATE TABLE devices (
    device_id varchar(255) PRIMARY KEY,
    device_type varchar(64) NOT NULL,
    is_bridge boolean NOT NULL DEFAULT false,
    last_seen_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT current_timestamp
);
