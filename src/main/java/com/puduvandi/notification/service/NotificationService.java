package com.puduvandi.notification.service;

import com.puduvandi.common.enums.NotificationStatus;
import com.puduvandi.common.enums.NotificationType;
import com.puduvandi.errorlog.service.ErrorLogService;
import com.puduvandi.notification.entity.NotificationLog;
import com.puduvandi.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Records every SMS/WhatsApp message the platform attempts to send to
 * notification_logs, and would dispatch it via an SMS/WhatsApp provider.
 * <p>
 * No provider is wired in right now (Twilio was removed — its free tier
 * didn't cover the actual use case; a replacement provider is TBD). Until
 * one is plugged into {@link #attemptSend}, every send is logged as FAILED
 * with a clear reason and never retried, so booking/handover flows keep
 * working exactly as if messaging were a real, currently-down channel.
 * Never throws — failures are logged (to notification_logs and error_logs)
 * and swallowed so a messaging outage can never break booking/ride flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_RETRIES = 3;
    private static final String NO_PROVIDER_MESSAGE =
            "No SMS/WhatsApp provider is configured (Twilio was removed) — wire a replacement provider into NotificationService.attemptSend().";

    private final NotificationLogRepository notificationLogRepository;
    private final ErrorLogService errorLogService;

    @Transactional
    public void sendSMS(Long bookingId, String customerPhone, String messageContent) {
        String formattedPhone = formatPhoneNumber(customerPhone);
        NotificationLog notificationLog = notificationLogRepository.save(
                NotificationLog.builder()
                        .bookingId(bookingId)
                        .customerPhone(formattedPhone)
                        .messageContent(messageContent)
                        .notificationType(NotificationType.SMS)
                        .status(NotificationStatus.PENDING)
                        .build());

        attemptSend(notificationLog, formattedPhone, messageContent, false);
    }

    @Transactional
    public void sendWhatsApp(Long bookingId, String customerPhone, String messageContent) {
        String formattedPhone = formatPhoneNumber(customerPhone);
        NotificationLog notificationLog = notificationLogRepository.save(
                NotificationLog.builder()
                        .bookingId(bookingId)
                        .customerPhone(formattedPhone)
                        .messageContent(messageContent)
                        .notificationType(NotificationType.WHATSAPP)
                        .status(NotificationStatus.PENDING)
                        .build());

        attemptSend(notificationLog, formattedPhone, messageContent, true);
    }

    @Transactional
    public void sendBoth(Long bookingId, String customerPhone, String messageContent) {
        sendSMS(bookingId, customerPhone, messageContent);
        sendWhatsApp(bookingId, customerPhone, messageContent);
    }

    /**
     * Finds FAILED notifications with fewer than MAX_RETRIES attempts and re-sends them.
     * Called by NotificationRetryTask every 15 minutes. With no provider configured,
     * every send is marked non-retryable up front, so this normally finds nothing.
     */
    @Transactional
    public void retryFailedNotifications() {
        List<NotificationLog> failed = notificationLogRepository
                .findByStatusAndRetryCountLessThan(NotificationStatus.FAILED, MAX_RETRIES);

        if (failed.isEmpty()) {
            return;
        }
        log.info("Retrying {} failed notification(s)", failed.size());

        for (NotificationLog notificationLog : failed) {
            notificationLog.setRetryCount(notificationLog.getRetryCount() + 1);
            boolean whatsapp = notificationLog.getNotificationType() == NotificationType.WHATSAPP;
            attemptSend(notificationLog, notificationLog.getCustomerPhone(),
                    notificationLog.getMessageContent(), whatsapp);
        }
    }

    /**
     * Formats an Indian phone number to E.164 form: +91XXXXXXXXXX.
     * Accepts 10-digit numbers, numbers with a leading 0, spaces/hyphens, or an existing +91 prefix.
     */
    public String formatPhoneNumber(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");

        if (digits.startsWith("91") && digits.length() == 12) {
            return "+" + digits;
        }
        if (digits.startsWith("0") && digits.length() == 11) {
            digits = digits.substring(1);
        }
        return "+91" + digits;
    }

    // ===== Internal =====

    /**
     * Stub send: no provider is wired in yet, so this just records the attempt
     * as a non-retryable failure instead of actually dispatching anything.
     * Replace this body with the new provider's send call once one is chosen —
     * every caller above already persists/audits correctly and won't need to change.
     */
    private void attemptSend(NotificationLog notificationLog, String toPhone, String messageContent,
                              boolean whatsapp) {
        log.warn("{} not sent (no provider configured): bookingId={}, phone={}, message=\"{}\"",
                whatsapp ? "WhatsApp" : "SMS", notificationLog.getBookingId(), toPhone, messageContent);

        notificationLog.setStatus(NotificationStatus.FAILED);
        notificationLog.setErrorMessage(NO_PROVIDER_MESSAGE);
        notificationLog.setRetryCount(MAX_RETRIES); // never retry until a provider exists
        notificationLogRepository.save(notificationLog);

        errorLogService.logServiceError(
                new IllegalStateException(NO_PROVIDER_MESSAGE), "NotificationLog", notificationLog.getId(), null);
    }
}
