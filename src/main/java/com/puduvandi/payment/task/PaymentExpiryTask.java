package com.puduvandi.payment.task;

import com.puduvandi.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background sweep that cancels bookings abandoned mid-payment, releasing
 * the bike instead of holding it reserved forever. Runs more often than the
 * expiry window itself (payment-expiry-minutes, 15 by default) so a stale
 * booking isn't held much longer than that window in practice.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryTask {

    private final PaymentService paymentService;

    @Scheduled(initialDelay = 300000, fixedDelay = 300000) // every 5 minutes
    public void expireStalePendingBookings() {
        try {
            paymentService.expireStalePendingBookings();
        } catch (Exception ex) {
            log.error("Payment expiry sweep failed", ex);
        }
    }
}
