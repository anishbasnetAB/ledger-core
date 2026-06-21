ALTER TABLE account
    ADD COLUMN balance NUMERIC(19,2) NOT NULL DEFAULT 0;

UPDATE account a
SET balance = COALESCE(x.derived_balance, 0)
FROM (
    SELECT
        account_id,
        SUM(
            CASE
                WHEN entry_type = 'CREDIT' THEN amount
                WHEN entry_type = 'DEBIT' THEN -amount
                ELSE 0
            END
        ) AS derived_balance
    FROM ledger_entry
    GROUP BY account_id
) x
WHERE a.id = x.account_id;