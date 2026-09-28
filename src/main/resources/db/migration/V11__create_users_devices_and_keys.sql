CREATE TABLE users (
    id bigserial PRIMARY KEY,
    user_id varchar(255) NOT NULL UNIQUE,
    vpa varchar(255) NOT NULL UNIQUE,
    display_name varchar(255) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT users_status_valid CHECK (status IN ('ACTIVE','INACTIVE'))
);

INSERT INTO users(user_id,vpa,display_name)
SELECT vpa,vpa,holder_name FROM accounts ON CONFLICT (vpa) DO NOTHING;

-- V4 already created devices. Preserve those rows, but do not trust an
-- unowned legacy device to authorize a payment.
INSERT INTO users(user_id,vpa,display_name,status)
SELECT 'legacy@unassigned','legacy@unassigned','Legacy devices','INACTIVE'
WHERE EXISTS (SELECT 1 FROM devices)
ON CONFLICT (vpa) DO NOTHING;

ALTER TABLE devices ADD COLUMN id bigserial;
ALTER TABLE devices ADD COLUMN user_id bigint;
ALTER TABLE devices ADD COLUMN device_name varchar(255);
ALTER TABLE devices ADD COLUMN status varchar(32) NOT NULL DEFAULT 'INACTIVE';
ALTER TABLE devices ADD COLUMN internet_capability boolean NOT NULL DEFAULT false;
ALTER TABLE devices ADD COLUMN trust_status varchar(32) NOT NULL DEFAULT 'UNTRUSTED';
ALTER TABLE devices ADD COLUMN registered_at timestamptz;
ALTER TABLE devices ADD COLUMN version bigint NOT NULL DEFAULT 0;
UPDATE devices SET user_id=(SELECT id FROM users WHERE vpa='legacy@unassigned'),
    device_name=device_id, internet_capability=is_bridge, registered_at=created_at;
ALTER TABLE devices ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE devices ALTER COLUMN device_name SET NOT NULL;
ALTER TABLE devices ALTER COLUMN registered_at SET NOT NULL;
ALTER TABLE devices DROP CONSTRAINT devices_pkey;
ALTER TABLE devices ADD CONSTRAINT devices_pkey PRIMARY KEY(id);
ALTER TABLE devices ADD CONSTRAINT devices_device_id_unique UNIQUE(device_id);
ALTER TABLE devices ADD CONSTRAINT devices_user_fk FOREIGN KEY(user_id) REFERENCES users(id);
ALTER TABLE devices ADD CONSTRAINT devices_status_valid CHECK (status IN ('ACTIVE','INACTIVE','REVOKED'));
ALTER TABLE devices ADD CONSTRAINT devices_trust_valid CHECK (trust_status IN ('TRUSTED','UNTRUSTED','BLOCKED'));
ALTER TABLE devices DROP COLUMN device_type;
ALTER TABLE devices DROP COLUMN is_bridge;
ALTER TABLE devices DROP COLUMN created_at;
CREATE INDEX idx_devices_user_id ON devices(user_id);
CREATE INDEX idx_devices_status ON devices(status);
CREATE INDEX idx_devices_trust_status ON devices(trust_status);

CREATE TABLE device_keys (
    id bigserial PRIMARY KEY,
    device_id bigint NOT NULL REFERENCES devices(id),
    public_key text NOT NULL,
    algorithm varchar(32) NOT NULL,
    key_fingerprint varchar(64) NOT NULL UNIQUE,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    CONSTRAINT device_keys_algorithm_valid CHECK (algorithm='Ed25519')
);
CREATE INDEX idx_device_keys_device_id ON device_keys(device_id);
CREATE UNIQUE INDEX idx_device_keys_one_active ON device_keys(device_id) WHERE active;
