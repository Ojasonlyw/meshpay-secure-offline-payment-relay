-- Unique/primary constraints already index packet hash and device ID.
CREATE INDEX idx_transactions_sender ON transactions(sender_vpa);
CREATE INDEX idx_transactions_receiver ON transactions(receiver_vpa);
CREATE INDEX idx_transactions_settled_at ON transactions(settled_at);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_ledger_account ON ledger(account_vpa);
CREATE INDEX idx_ledger_transaction ON ledger(transaction_id);
CREATE INDEX idx_devices_last_seen ON devices(last_seen_at);
