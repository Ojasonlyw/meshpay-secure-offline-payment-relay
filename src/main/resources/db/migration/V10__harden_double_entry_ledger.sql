-- Freeze writers while establishing the opening-balance baseline.
LOCK TABLE accounts, ledger_entries IN ACCESS EXCLUSIVE MODE;
ALTER TABLE accounts ADD COLUMN opening_balance numeric(19,2) NOT NULL DEFAULT 0;
UPDATE accounts a
SET opening_balance = a.balance
    - COALESCE((SELECT sum(amount) FROM ledger_entries WHERE account_vpa=a.vpa AND direction='CREDIT'), 0)
    + COALESCE((SELECT sum(amount) FROM ledger_entries WHERE account_vpa=a.vpa AND direction='DEBIT'), 0);

CREATE FUNCTION protect_opening_balance() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.opening_balance IS DISTINCT FROM OLD.opening_balance THEN
        RAISE EXCEPTION 'Account opening balance is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER accounts_opening_balance_immutable BEFORE UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION protect_opening_balance();

CREATE FUNCTION protect_ledger_history() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Ledger entries are immutable' USING ERRCODE='23514';
END $$;
CREATE TRIGGER ledger_entries_immutable BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION protect_ledger_history();
CREATE TRIGGER ledger_entries_no_truncate BEFORE TRUNCATE ON ledger_entries
    FOR EACH STATEMENT EXECUTE FUNCTION protect_ledger_history();

-- Existing indexes cover account, transaction, creation time, and payment (unique pair).
CREATE INDEX idx_ledger_entries_direction ON ledger_entries(direction);

-- V9 already enforces the matching transaction and exactly one leg of each direction.
DO $$
DECLARE p record;
BEGIN
    FOR p IN SELECT id FROM payments LOOP PERFORM check_payment_ledger(p.id); END LOOP;
END $$;
