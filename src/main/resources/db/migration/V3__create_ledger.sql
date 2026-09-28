CREATE TABLE ledger (
    id bigserial PRIMARY KEY,
    transaction_id bigint NOT NULL,
    account_vpa varchar(255) NOT NULL,
    direction varchar(6) NOT NULL,
    amount numeric(19,2) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT current_timestamp,
    CONSTRAINT ledger_transaction_fk FOREIGN KEY (transaction_id) REFERENCES transactions(id),
    CONSTRAINT ledger_account_fk FOREIGN KEY (account_vpa) REFERENCES accounts(vpa),
    CONSTRAINT ledger_direction_valid CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ledger_amount_positive CHECK (amount > 0)
);
