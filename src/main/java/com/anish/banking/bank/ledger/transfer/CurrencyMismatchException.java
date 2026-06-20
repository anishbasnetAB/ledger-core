package com.anish.banking.bank.ledger.transfer;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String from, String to) {
        super("Currency mismatch: " + from + " -> " + to);
    }
}