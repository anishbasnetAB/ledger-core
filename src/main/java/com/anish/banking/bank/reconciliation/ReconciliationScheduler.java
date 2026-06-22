package com.anish.banking.bank.reconciliation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliationService;

    public ReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }


    @Scheduled(cron = "${reconciliation.cron:0 0 3 * * *}")
    public void scheduledReconciliation() {
        log.info("Starting scheduled reconciliation sweep");
        List<ReconciliationBreak> breaks = reconciliationService.runReconciliation();
        log.info("Scheduled reconciliation sweep finished: {} break(s) detected", breaks.size());
    }
}
