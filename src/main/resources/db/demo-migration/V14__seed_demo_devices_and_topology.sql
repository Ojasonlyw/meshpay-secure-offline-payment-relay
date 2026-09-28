INSERT INTO users(user_id,vpa,display_name,status)
SELECT a.vpa,a.vpa,a.holder_name,'ACTIVE' FROM accounts a
WHERE a.vpa IN ('alice@demo','bob@demo','carol@demo','charlie@demo','dave@demo')
ON CONFLICT (vpa) DO NOTHING;

INSERT INTO devices(device_id,user_id,device_name,status,internet_capability,trust_status,registered_at,version)
SELECT seed.device_id,u.id,seed.device_name,'ACTIVE',seed.internet,'TRUSTED',now(),0
FROM (VALUES
    ('phone-alice','alice@demo','Alice phone',false),
    ('phone-stranger1','bob@demo','Bob phone',false),
    ('phone-stranger2','carol@demo','Carol phone',false),
    ('phone-stranger3','charlie@demo','Charlie phone',false),
    ('phone-bridge','dave@demo','Bridge phone',true)
) AS seed(device_id,vpa,device_name,internet)
JOIN users u ON u.vpa=seed.vpa
ON CONFLICT (device_id) DO UPDATE SET user_id=excluded.user_id,
    device_name=excluded.device_name,status='ACTIVE',internet_capability=excluded.internet_capability,
    trust_status='TRUSTED';

INSERT INTO mesh_connections(source_device_id,target_device_id,status,link_type)
SELECT source.id,target.id,'ACTIVE','BLUETOOTH'
FROM (VALUES
    ('phone-alice','phone-stranger1'),
    ('phone-stranger1','phone-stranger2'),
    ('phone-stranger2','phone-stranger3'),
    ('phone-stranger3','phone-bridge')
) AS seed(source_id,target_id)
JOIN devices source ON source.device_id=seed.source_id
JOIN devices target ON target.device_id=seed.target_id
ON CONFLICT (source_device_id,target_device_id) DO NOTHING;
