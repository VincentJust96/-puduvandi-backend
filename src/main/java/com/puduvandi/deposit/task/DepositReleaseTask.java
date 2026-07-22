package com.puduvandi.deposit.task;

import com.puduvandi.config.DepositProperties;
import com.puduvandi.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background sweep that auto-refunds a completed booking's security deposit
 * once the grace period passes with no deduction claim filed against it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositReleaseTask {

    private final PaymentService paymentService;
    private final DepositProperties depositProperties;

    @Scheduled(initialDelay = 600000, fixedDelay = 3600000) // every hour
    public void releaseUnclaimedDeposits() {
        try {
            paymentService.releaseUnclaimedDeposits(depositProperties.getAutoReleaseHours());
        } catch (Exception ex) {
            log.error("Deposit auto-release sweep failed", ex);
        }
    }
}
