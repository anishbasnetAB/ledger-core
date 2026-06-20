package com.anish.banking.bank.ledger.transfer;

public class SameAccountTransferException extends RuntimeException {
    public SameAccountTransferException() { super("Source and destination must differ"); }
}

