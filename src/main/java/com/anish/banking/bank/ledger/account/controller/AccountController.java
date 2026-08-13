package com.anish.banking.bank.ledger.account.controller;

import com.anish.banking.bank.ledger.account.dto.AccountResponse;
import com.anish.banking.bank.ledger.account.dto.BalanceResponse;
import com.anish.banking.bank.ledger.account.dto.CreateAccountRequest;
import com.anish.banking.bank.ledger.account.dto.MoneyRequest;
import com.anish.banking.bank.ledger.account.service.BalanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final BalanceService balanceService;

    public AccountController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse body = balanceService.createAccount(request.ownerName(), request.currency());
        return ResponseEntity.created(URI.create("/api/accounts/" + body.id())).body(body);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        return balanceService.getBalance(id);
    }

    @PostMapping("/{id}/deposit")
    public BalanceResponse deposit(
            @PathVariable Long id,
            @Valid @RequestBody MoneyRequest request
    ) {
        return balanceService.deposit(id, request.amount());
    }

    @PostMapping("/{id}/withdraw")
    public BalanceResponse withdraw(
            @PathVariable Long id,
            @Valid @RequestBody MoneyRequest request
    ) {
        return balanceService.withdraw(id, request.amount());
    }
}
