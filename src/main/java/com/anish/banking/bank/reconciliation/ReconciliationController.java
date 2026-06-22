package com.anish.banking.bank.reconciliation;

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

    // TODO: restrict to ADMIN role once auth lands
    @PostMapping("/run")
    public void run() {
        reconciliationService.runReconciliation();
    }

    // TODO: restrict to ADMIN role once auth lands
    @GetMapping("/breaks")
    public List<ReconciliationBreakResponse> breaks() {
        return breaks.findAll().stream()
                .map(ReconciliationBreakResponse::from)
                .toList();
    }
}
