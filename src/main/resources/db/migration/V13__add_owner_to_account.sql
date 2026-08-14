-- Ownership: every account now belongs to exactly one user (closes the IDOR hole where any
-- authenticated user could act on any account id). The demo accounts seeded before auth even
-- existed (V3's two customer accounts, V7's settlement account) have no real owner to
-- backfill from, so they go to a dedicated placeholder user rather than being left ownerless
-- -- a NOT NULL column can't stay null anyway, and "ownerless" would read as "nobody may
-- touch this", not "anyone may", which is the wrong default to leave lying around.
INSERT INTO users (email, password_hash, role)
VALUES ('legacy-seed-owner@ledger-core.internal', '!no-login-seed-account', 'USER')
ON CONFLICT (email) DO NOTHING;

ALTER TABLE account ADD COLUMN owner_user_id BIGINT REFERENCES users(id);

UPDATE account
SET owner_user_id = (SELECT id FROM users WHERE email = 'legacy-seed-owner@ledger-core.internal')
WHERE owner_user_id IS NULL;

ALTER TABLE account ALTER COLUMN owner_user_id SET NOT NULL;

CREATE INDEX idx_account_owner_user ON account(owner_user_id);
