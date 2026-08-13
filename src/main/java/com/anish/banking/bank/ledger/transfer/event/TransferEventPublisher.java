package com.anish.banking.bank.ledger.transfer.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransferEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(TransferEventPublisher.class);
    private static final String TOPIC = "transfer.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TransferEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // AFTER_COMMIT, not a plain @EventListener: if the transfer's transaction rolls back,
    // the event must never reach Kafka. If there's no transaction at all (shouldn't happen —
    // TransferService.transfer is @Transactional), Spring silently drops the event, which is
    // the right failure mode here (no publish beats a publish for money that never moved).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferCompleted(TransferCompletedEvent event) {
        // Keyed by transferId so retries/replays of the same transfer stay on one partition,
        // in order. A publish failure is logged and swallowed, never rethrown: the transfer
        // already committed, and this listener runs after that commit, so letting an exception
        // escape here would surface a false failure to a caller whose money already moved.
        try {
            kafkaTemplate.send(TOPIC, event.transferId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish TransferCompletedEvent for transfer {}",
                                    event.transferId(), ex);
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to publish TransferCompletedEvent for transfer {}", event.transferId(), ex);
        }
    }
}
