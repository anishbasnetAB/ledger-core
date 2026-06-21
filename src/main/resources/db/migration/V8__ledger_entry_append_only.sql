CREATE OR REPLACE FUNCTION reject_ledger_entry_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entry is append-only; % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entry_append_only
    BEFORE UPDATE OR DELETE ON ledger_entry
    FOR EACH STATEMENT
    EXECUTE FUNCTION reject_ledger_entry_mutation();