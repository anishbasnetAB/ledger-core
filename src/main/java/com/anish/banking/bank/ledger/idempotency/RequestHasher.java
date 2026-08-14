package com.anish.banking.bank.ledger.idempotency;

import com.anish.banking.bank.ledger.transfer.dto.CreateTransferRequest;

import java.math.BigDecimal;
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
        return sha256Hex(canonical);
    }

    // Same idea as the transfer hash above, for BalanceService's deposit/withdraw. The
    // operation name is part of the canonical string so a deposit and a withdraw of the same
    // amount on the same account never hash the same way and get mistaken for one another.
    public static String hash(String operation, Long accountId, BigDecimal amount) {
        String canonical = operation + "|" + accountId + "|" + amount.stripTrailingZeros().toPlainString();
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
