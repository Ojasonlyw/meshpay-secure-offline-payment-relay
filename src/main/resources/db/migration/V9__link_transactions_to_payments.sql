ALTER TABLE transactions ADD COLUMN payment_id bigint;
UPDATE transactions t SET payment_id=p.id FROM payments p WHERE p.packet_hash=t.packet_hash;
ALTER TABLE transactions ALTER COLUMN payment_id SET NOT NULL;
ALTER TABLE transactions ADD CONSTRAINT transactions_payment_fk FOREIGN KEY(payment_id) REFERENCES payments(id);
ALTER TABLE transactions ADD CONSTRAINT transactions_payment_unique UNIQUE(payment_id);
ALTER TABLE transactions ADD CONSTRAINT transactions_id_payment_unique UNIQUE(id,payment_id);
ALTER TABLE transactions DROP CONSTRAINT transactions_status_valid;
ALTER TABLE transactions ADD CONSTRAINT transactions_status_valid
    CHECK (status IN ('SETTLED','REJECTED','FAILED','EXPIRED'));
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_transaction_payment_fk
    FOREIGN KEY(transaction_id,payment_id) REFERENCES transactions(id,payment_id);

-- Deferred checks allow the payment, audit record, and both legs to commit together.
CREATE FUNCTION check_payment_ledger(p_id bigint) RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    p payments%ROWTYPE;
    tx transactions%ROWTYPE;
    legs integer;
BEGIN
    SELECT * INTO p FROM payments WHERE id=p_id;
    IF NOT FOUND THEN RETURN; END IF;
    SELECT count(*) INTO legs FROM ledger_entries WHERE payment_id=p_id;
    SELECT * INTO tx FROM transactions WHERE payment_id=p_id;
    IF FOUND THEN
        IF tx.packet_hash <> p.packet_hash OR tx.sender_vpa <> p.sender_vpa
           OR tx.receiver_vpa <> p.receiver_vpa OR tx.amount <> p.amount
           OR tx.status <> p.status THEN
            RAISE EXCEPTION 'Payment and transaction mismatch' USING ERRCODE='23514';
        END IF;
    END IF;
    IF p.status='SETTLED' THEN
        IF tx.id IS NULL OR tx.status <> 'SETTLED' OR legs <> 2 OR EXISTS (
            SELECT 1 FROM ledger_entries l WHERE l.payment_id=p_id AND
                (l.transaction_id <> tx.id OR l.amount <> p.amount OR
                 l.account_vpa <> CASE WHEN l.direction='DEBIT' THEN p.sender_vpa ELSE p.receiver_vpa END)
        ) THEN
            RAISE EXCEPTION 'Settled payment requires matching debit and credit' USING ERRCODE='23514';
        END IF;
    ELSIF legs <> 0 THEN
        RAISE EXCEPTION 'Non-settled payment cannot have ledger entries' USING ERRCODE='23514';
    END IF;
END $$;

CREATE FUNCTION enforce_payment_ledger() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_TABLE_NAME='payments' THEN
        IF TG_OP <> 'INSERT' THEN PERFORM check_payment_ledger(OLD.id); END IF;
        IF TG_OP <> 'DELETE' THEN PERFORM check_payment_ledger(NEW.id); END IF;
    ELSE
        IF TG_OP <> 'INSERT' THEN PERFORM check_payment_ledger(OLD.payment_id); END IF;
        IF TG_OP <> 'DELETE' THEN PERFORM check_payment_ledger(NEW.payment_id); END IF;
    END IF;
    RETURN NULL;
END $$;

CREATE CONSTRAINT TRIGGER payments_ledger_integrity AFTER INSERT OR UPDATE OR DELETE ON payments
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_payment_ledger();
CREATE CONSTRAINT TRIGGER transactions_ledger_integrity AFTER INSERT OR UPDATE OR DELETE ON transactions
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_payment_ledger();
CREATE CONSTRAINT TRIGGER ledger_entries_integrity AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION enforce_payment_ledger();

DO $$
DECLARE p record;
BEGIN
    FOR p IN SELECT id FROM payments LOOP PERFORM check_payment_ledger(p.id); END LOOP;
END $$;
