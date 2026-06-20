package com.anish.banking.bank.ledger.account;

import org.springframework.web.bind.annotation.*;

//The web layer — the only file that knows about HTTP. It maps GET /accounts/{id}/balance
//to the service call and returns the DTO, which Spring serializes to JSON. It contains no business logic, by design.

@RestController
@RequestMapping("/accounts")
public class AccountController {
    private final BalanceService balanceService;

    public AccountController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        return balanceService.getBalance(id);
    }
}