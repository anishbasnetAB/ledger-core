package com.anish.banking.bank.ledger.transfer.exception;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String from, String to) {
        super("Currency mismatch: " + from + " -> " + to);
    }
}
