ALTER TABLE account
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER'
        CHECK (account_type IN ('CUSTOMER', 'SETTLEMENT'));

CREATE UNIQUE INDEX uq_one_settlement_per_currency
    ON account (currency)
    WHERE account_type = 'SETTLEMENT';

INSERT INTO account (owner_name, currency, account_type)
VALUES ('System Settlement', 'CAD', 'SETTLEMENT');