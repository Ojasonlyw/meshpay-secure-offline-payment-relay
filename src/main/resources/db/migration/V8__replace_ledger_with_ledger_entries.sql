ALTER TABLE ledger RENAME TO ledger_entries;
ALTER TABLE ledger_entries ADD COLUMN payment_id bigint;
UPDATE ledger_entries l SET payment_id=p.id
FROM transactions t JOIN payments p ON p.packet_hash=t.packet_hash
WHERE l.transaction_id=t.id;

-- Fail explicitly on inconsistent history rather than repairing financial evidence.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM ledger_entries l JOIN transactions t ON t.id=l.transaction_id
        WHERE t.status <> 'SETTLED' OR l.amount <> t.amount
           OR l.account_vpa <> CASE WHEN l.direction='DEBIT' THEN t.sender_vpa ELSE t.receiver_vpa END
    ) OR EXISTS (
        SELECT 1 FROM ledger_entries GROUP BY payment_id,direction HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Inconsistent historical ledger; manual reconciliation required';
    END IF;
END $$;

ALTER TABLE ledger_entries ALTER COLUMN payment_id SET NOT NULL;
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_payment_fk FOREIGN KEY(payment_id) REFERENCES payments(id);
ALTER TABLE ledger_entries ADD CONSTRAINT ledger_payment_direction_unique UNIQUE(payment_id,direction);
INSERT INTO ledger_entries(payment_id,transaction_id,account_vpa,direction,amount,created_at)
SELECT p.id,t.id,CASE WHEN d.direction='DEBIT' THEN t.sender_vpa ELSE t.receiver_vpa END,
       d.direction,t.amount,t.settled_at
FROM transactions t JOIN payments p ON p.packet_hash=t.packet_hash
CROSS JOIN (VALUES ('DEBIT'),('CREDIT')) d(direction)
WHERE t.status='SETTLED'
AND NOT EXISTS (SELECT 1 FROM ledger_entries l WHERE l.payment_id=p.id AND l.direction=d.direction);

ALTER INDEX idx_ledger_account RENAME TO idx_ledger_entries_account;
ALTER INDEX idx_ledger_transaction RENAME TO idx_ledger_entries_transaction;
CREATE INDEX idx_ledger_entries_created ON ledger_entries(created_at);
