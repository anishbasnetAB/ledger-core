package com.anish.banking.bank.reconciliation.controller;

import com.anish.banking.bank.reconciliation.dto.ReconciliationBreakResponse;
import com.anish.banking.bank.reconciliation.repository.ReconciliationBreakRepository;
import com.anish.banking.bank.reconciliation.service.ReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/admin/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationBreakRepository breaks;

    public ReconciliationController(
            ReconciliationService reconciliationService,
            ReconciliationBreakRepository breaks
    ) {
        this.reconciliationService = reconciliationService;
        this.breaks = breaks;
    }

    // Restricted to ROLE_ADMIN in SecurityConfig.
    @PostMapping("/run")
    public void run() {
        reconciliationService.runReconciliation();
    }

    // Restricted to ROLE_ADMIN in SecurityConfig.
    @GetMapping("/breaks")
    public List<ReconciliationBreakResponse> breaks() {
        return breaks.findAll().stream()
                .map(ReconciliationBreakResponse::from)
                .toList();
    }
}
