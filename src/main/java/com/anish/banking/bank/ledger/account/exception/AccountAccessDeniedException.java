package com.anish.banking.bank.ledger.account.exception;

/**
 * Thrown when the caller references an account (by path variable, or as a transfer's source)
 * that isn't theirs. Deliberately carries no account id in the message — same reasoning as
 * InvalidCredentialsException: the point of a 403 here is to look identical whether the
 * account belongs to someone else or doesn't exist at all, so nothing account-specific can
 * leak through the error body.
 */
public class AccountAccessDeniedException extends RuntimeException {
    public AccountAccessDeniedException() {
        super("You do not have access to this account");
    }
}
