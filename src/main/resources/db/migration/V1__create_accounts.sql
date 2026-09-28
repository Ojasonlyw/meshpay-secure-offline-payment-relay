CREATE TABLE accounts (
    vpa varchar(255) PRIMARY KEY,
    holder_name varchar(255) NOT NULL,
    balance numeric(19,2) NOT NULL,
    version bigint,
    CONSTRAINT accounts_balance_nonnegative CHECK (balance >= 0)
);
