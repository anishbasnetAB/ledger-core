package com.anish.banking.bank.ledger.idempotency;

import com.anish.banking.bank.ledger.transfer.CreateTransferRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestHasher {

    private RequestHasher() {}

    public static String hash(CreateTransferRequest req) {
        String canonical = req.sourceAccountId()
                + "|" + req.destinationAccountId()
                + "|" + req.amount().stripTrailingZeros().toPlainString();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}