package com.anish.banking.bank.ledger.transfer.controller;

import com.anish.banking.bank.auth.security.AuthenticatedUser;
import com.anish.banking.bank.ledger.transfer.dto.CreateTransferRequest;
import com.anish.banking.bank.ledger.transfer.dto.TransferResponse;
import com.anish.banking.bank.ledger.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransferRequest req) {
        // Destination can be anyone's account; TransferService enforces that the source has
        // to be the caller's own.
        TransferResponse body = transferService.transfer(req, idempotencyKey, caller.id());
        return ResponseEntity.created(URI.create("/api/transfers/" + body.transferId())).body(body);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable Long id) {
        return transferService.getTransfer(id);
    }
}
