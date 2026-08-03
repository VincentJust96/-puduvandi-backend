package com.puduvandi.notification.service;

import com.puduvandi.common.enums.NotificationPurpose;
import com.puduvandi.common.enums.NotificationStatus;
import com.puduvandi.common.enums.NotificationType;
import com.puduvandi.config.Msg91Properties;
import com.puduvandi.errorlog.service.ErrorLogService;
import com.puduvandi.notification.client.Msg91Client;
import com.puduvandi.notification.client.Msg91SendResult;
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
 * notification_logs, and dispatches SMS via MSG91 once it's configured.
 * <p>
 * WhatsApp has no provider wired in (Meta's WhatsApp Business template
 * approval is a separate process from India's SMS DLT registration — out of
 * scope until that's decided) — every WhatsApp send is logged as FAILED and
 * never retried, same as before. SMS dispatches via MSG91 when
 * {@link Msg91Properties#isConfigured()} and a DLT template ID exists for the
 * given {@link NotificationPurpose}; otherwise it falls back to the same
 * no-provider stub. Never throws — failures are logged (to notification_logs
 * and error_logs) and swallowed so a messaging outage can never break
 * booking/ride flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_RETRIES = 3;
    private static final String NO_PROVIDER_MESSAGE =
            "No SMS/WhatsApp provider is configured for this message type — see automation/n8n/MSG91_SETUP.md.";

    private final NotificationLogRepository notificationLogRepository;
    private final ErrorLogService errorLogService;
    private final Msg91Client msg91Client;
    private final Msg91Properties msg91Properties;

    @Transactional
    public void sendSMS(Long bookingId, String customerPhone, String messageContent, NotificationPurpose purpose) {
        String formattedPhone = formatPhoneNumber(customerPhone);
        NotificationLog notificationLog = notificationLogRepository.save(
                NotificationLog.builder()
                        .bookingId(bookingId)
                        .customerPhone(formattedPhone)
                        .messageContent(messageContent)
                        .notificationType(NotificationType.SMS)
                        .purpose(purpose)
                        .status(NotificationStatus.PENDING)
                        .build());

        attemptSend(notificationLog, formattedPhone, messageContent, false, purpose);
    }

    @Transactional
    public void sendWhatsApp(Long bookingId, String customerPhone, String messageContent, NotificationPurpose purpose) {
        String formattedPhone = formatPhoneNumber(customerPhone);
        NotificationLog notificationLog = notificationLogRepository.save(
                NotificationLog.builder()
                        .bookingId(bookingId)
                        .customerPhone(formattedPhone)
                        .messageContent(messageContent)
                        .notificationType(NotificationType.WHATSAPP)
                        .purpose(purpose)
                        .status(NotificationStatus.PENDING)
                        .build());

        attemptSend(notificationLog, formattedPhone, messageContent, true, purpose);
    }

    @Transactional
    public void sendBoth(Long bookingId, String customerPhone, String messageContent, NotificationPurpose purpose) {
        sendSMS(bookingId, customerPhone, messageContent, purpose);
        sendWhatsApp(bookingId, customerPhone, messageContent, purpose);
    }

    /**
     * Finds FAILED notifications with fewer than MAX_RETRIES attempts and re-sends them.
     * Called by NotificationRetryTask every 15 minutes. Sends with no provider/template
     * configured are marked non-retryable up front, so this normally only finds genuine
     * transient MSG91 failures.
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
                    notificationLog.getMessageContent(), whatsapp, notificationLog.getPurpose());
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
     * WhatsApp has no provider wired in — always falls back to the stub.
     * SMS dispatches via MSG91 once configured with a template for this purpose;
     * otherwise it falls back to the same stub as before.
     */
    private void attemptSend(NotificationLog notificationLog, String toPhone, String messageContent,
                              boolean whatsapp, NotificationPurpose purpose) {
        if (!whatsapp && msg91Properties.isConfigured()) {
            String templateId = resolveTemplateId(purpose);
            Msg91SendResult result = msg91Client.send(toPhone, messageContent, templateId);

            if (result.success()) {
                notificationLog.setStatus(NotificationStatus.SENT);
                notificationLog.setSentAt(LocalDateTime.now());
                notificationLogRepository.save(notificationLog);
                return;
            }

            log.warn("SMS send failed via MSG91: bookingId={}, phone={}, reason={}",
                    notificationLog.getBookingId(), toPhone, result.errorMessage());
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setErrorMessage(result.errorMessage());
            notificationLogRepository.save(notificationLog);
            errorLogService.logServiceError(
                    new IllegalStateException(result.errorMessage()), "NotificationLog", notificationLog.getId(), null);
            return;
        }

        log.warn("{} not sent (no provider configured): bookingId={}, phone={}, message=\"{}\"",
                whatsapp ? "WhatsApp" : "SMS", notificationLog.getBookingId(), toPhone, messageContent);

        notificationLog.setStatus(NotificationStatus.FAILED);
        notificationLog.setErrorMessage(NO_PROVIDER_MESSAGE);
        notificationLog.setRetryCount(MAX_RETRIES); // never retry until a provider/template exists
        notificationLogRepository.save(notificationLog);

        errorLogService.logServiceError(
                new IllegalStateException(NO_PROVIDER_MESSAGE), "NotificationLog", notificationLog.getId(), null);
    }

    private String resolveTemplateId(NotificationPurpose purpose) {
        return switch (purpose) {
            case BOOKING_CONFIRMATION -> msg91Properties.getBookingConfirmationTemplateId();
            case PICKUP_REMINDER -> msg91Properties.getPickupReminderTemplateId();
            case RIDE_COMPLETION -> msg91Properties.getRideCompletionTemplateId();
            case OTP -> msg91Properties.getOtpTemplateId();
            case ADHOC -> null; // no fixed template for free-text admin sends — see NotificationPurpose.ADHOC
        };
    }
}
