INSERT INTO accounts (vpa, holder_name, balance, version) VALUES
    ('alice@demo', 'Alice', 5000.00, 0),
    ('bob@demo', 'Bob', 1000.00, 0),
    ('carol@demo', 'Carol', 2500.00, 0),
    ('charlie@demo', 'Charlie', 2500.00, 0),
    ('dave@demo', 'Dave', 500.00, 0)
ON CONFLICT (vpa) DO NOTHING;
