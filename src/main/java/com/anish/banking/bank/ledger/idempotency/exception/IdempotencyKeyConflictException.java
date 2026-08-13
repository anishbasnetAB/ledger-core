package com.anish.banking.bank.ledger.idempotency.exception;

public class IdempotencyKeyConflictException extends RuntimeException {
    public IdempotencyKeyConflictException(String key) {
        super("Idempotency key '" + key + "' was already used for a different request");
    }
}
