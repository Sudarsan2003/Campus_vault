package org.example.cvbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.cvbackend.blockchain.BlockchainService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically records loans that have passed their due date on the blockchain. */
@Component
@ConditionalOnProperty(name = "app.loans.overdue-monitor", havingValue = "true", matchIfMissing = true)
public class OverdueMonitor {

    private static final Logger log = LoggerFactory.getLogger(OverdueMonitor.class);

    private final LoanService loans;
    private final BlockchainService chain;

    public OverdueMonitor(LoanService loans, BlockchainService chain) {
        this.loans = loans;
        this.chain = chain;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        try {
            chain.syncDevClock();
        } catch (RuntimeException e) {
            log.warn("Couldn't sync the local chain clock: {}", e.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${app.loans.overdue-check-ms:60000}",
               fixedDelayString = "${app.loans.overdue-check-ms:60000}")
    public void check() {
        try {
            chain.syncDevClock(); // in case Anvil was restarted while the backend kept running
            int n = loans.flagNewlyOverdue();
            if (n > 0) log.info("Marked {} loan(s) overdue on-chain", n);
        } catch (RuntimeException e) {
            // Chain or database briefly unavailable: try again on the next run.
            log.warn("Overdue check skipped: {}", e.getMessage());
        }
    }
}
