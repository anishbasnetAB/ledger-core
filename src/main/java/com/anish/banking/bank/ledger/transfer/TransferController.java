package com.anish.banking.bank.ledger.transfer;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/transfers")
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> create(@Valid @RequestBody CreateTransferRequest req) {
        TransferResponse body = transferService.transfer(req);
        return ResponseEntity.created(URI.create("/transfers/" + body.transferId())).body(body);
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable Long id) {
        return transferService.getTransfer(id);
    }
}