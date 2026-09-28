ALTER TABLE ledger_entries DISABLE TRIGGER ledger_entries_no_truncate;
TRUNCATE ledger_entries, transactions, packet_routes, payment_signatures, payments,
    mesh_connections, device_keys, devices, users, accounts RESTART IDENTITY;
ALTER TABLE ledger_entries ENABLE TRIGGER ledger_entries_no_truncate;
INSERT INTO accounts (vpa, holder_name, balance, opening_balance, version) VALUES
    ('alice@demo', 'Alice', 5000.00, 5000.00, 0),
    ('bob@demo', 'Bob', 1000.00, 1000.00, 0),
    ('carol@demo', 'Carol', 2500.00, 2500.00, 0),
    ('charlie@demo', 'Charlie', 2500.00, 2500.00, 0),
    ('dave@demo', 'Dave', 500.00, 500.00, 0);
INSERT INTO users(user_id,vpa,display_name,status)
SELECT vpa,vpa,holder_name,'ACTIVE' FROM accounts;
INSERT INTO devices(device_id,user_id,device_name,status,internet_capability,trust_status,registered_at,version)
SELECT seed.device_id,u.id,seed.device_name,'ACTIVE',seed.internet,'TRUSTED',now(),0
FROM (VALUES
    ('phone-alice','alice@demo','Alice phone',false),
    ('phone-stranger1','bob@demo','Bob phone',false),
    ('phone-stranger2','carol@demo','Carol phone',false),
    ('phone-stranger3','charlie@demo','Charlie phone',false),
    ('phone-bridge','dave@demo','Bridge phone',true)
) AS seed(device_id,vpa,device_name,internet) JOIN users u ON u.vpa=seed.vpa;
INSERT INTO mesh_connections(source_device_id,target_device_id,status,link_type)
SELECT source.id,target.id,'ACTIVE','BLUETOOTH'
FROM (VALUES
    ('phone-alice','phone-stranger1'),
    ('phone-stranger1','phone-stranger2'),
    ('phone-stranger2','phone-stranger3'),
    ('phone-stranger3','phone-bridge')
) AS seed(source_id,target_id)
JOIN devices source ON source.device_id=seed.source_id
JOIN devices target ON target.device_id=seed.target_id;
