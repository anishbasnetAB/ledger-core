package com.anish.banking.bank.ledger.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

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

    public record MoneyRequest(
            @NotNull
            @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
            BigDecimal amount
    ) {
    }
}