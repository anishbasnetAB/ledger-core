package com.anish.banking.bank.reconciliation;

/**
 * Lifecycle of a reconciliation break.
 *
 * OPEN     — the account's stored balance still disagrees with its ledger-derived
 *            balance. At most one OPEN break may exist per account (enforced by a
 *            partial unique index, see V11__reconciliation_break_status.sql).
 * RESOLVED — the account reconciled cleanly again. Terminal: a later mismatch opens
 *            a NEW break rather than reopening this one.
 */
public enum ReconciliationBreakStatus {
    OPEN,
    RESOLVED
}
