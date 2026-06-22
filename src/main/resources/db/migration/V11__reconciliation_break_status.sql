
ALTER TABLE reconciliation_breaks
    ADD COLUMN status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN last_detected_at TIMESTAMPTZ,
    ADD COLUMN resolved_at      TIMESTAMPTZ;

UPDATE reconciliation_breaks
SET last_detected_at = detected_at
WHERE last_detected_at IS NULL;


ALTER TABLE reconciliation_breaks
    ALTER COLUMN last_detected_at SET NOT NULL;


ALTER TABLE reconciliation_breaks
    ADD CONSTRAINT chk_reconciliation_break_status
        CHECK (status IN ('OPEN', 'RESOLVED'));

WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY account_id
               ORDER BY last_detected_at DESC, id DESC
           ) AS rn
    FROM reconciliation_breaks
    WHERE status = 'OPEN'
)
UPDATE reconciliation_breaks b
SET status      = 'RESOLVED',
    resolved_at = COALESCE(b.last_detected_at, now())
FROM ranked
WHERE b.id = ranked.id
  AND ranked.rn > 1;


CREATE UNIQUE INDEX uq_one_open_reconciliation_break_per_account
    ON reconciliation_breaks (account_id)
    WHERE status = 'OPEN';
