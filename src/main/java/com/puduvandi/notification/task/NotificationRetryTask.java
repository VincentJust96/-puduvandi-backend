package com.puduvandi.notification.task;

import com.puduvandi.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background retry sweep for notifications that failed to send.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryTask {

    private final NotificationService notificationService;

    @Scheduled(initialDelay = 900000, fixedDelay = 900000)
    public void retryFailedNotifications() {
        log.info("Starting scheduled notification retry sweep");
        try {
            notificationService.retryFailedNotifications();
        } catch (Exception ex) {
            log.error("Notification retry sweep failed", ex);
        }
        log.info("Completed scheduled notification retry sweep");
    }
}
