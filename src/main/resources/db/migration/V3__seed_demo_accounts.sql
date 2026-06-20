INSERT INTO account (owner_name, currency) VALUES ('Anish Basnet', 'CAD'); -- id 1
INSERT INTO account (owner_name, currency) VALUES ('Demo Payee',   'CAD'); -- id 2

INSERT INTO ledger_entry (account_id, entry_type, amount) VALUES (1, 'CREDIT', 1000.00);
INSERT INTO ledger_entry (account_id, entry_type, amount) VALUES (2, 'CREDIT',  250.00);