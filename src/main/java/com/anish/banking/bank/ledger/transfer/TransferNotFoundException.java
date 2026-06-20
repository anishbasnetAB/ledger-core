package com.anish.banking.bank.ledger.transfer;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(Long id) {
        super("Transfer not found: " + id);
    }
}