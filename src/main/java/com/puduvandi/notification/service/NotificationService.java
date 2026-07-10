package com.puduvandi.notification.service;

import com.puduvandi.common.enums.NotificationStatus;
import com.puduvandi.common.enums.NotificationType;
import com.puduvandi.config.TwilioConfig;
import com.puduvandi.errorlog.service.ErrorLogService;
import com.puduvandi.notification.entity.NotificationLog;
import com.puduvandi.notification.repository.NotificationLogRepository;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends booking-related SMS/WhatsApp messages via Twilio and logs every
 * attempt to notification_logs. Never throws — failures are logged
 * (to notification_logs and error_logs) and swallowed so a messaging
 * outage can never break booking/ride flows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_RETRIES = 3;

    private final NotificationLogRepository notificationLogRepository;
    private final TwilioConfig twilioConfig;
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

        attemptSend(notificationLog, formattedPhone, messageContent,
                twilioConfig.getPhoneNumber(), false);
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

        attemptSend(notificationLog, formattedPhone, messageContent,
                twilioConfig.getWhatsappNumber(), true);
    }

    @Transactional
    public void sendBoth(Long bookingId, String customerPhone, String messageContent) {
        sendSMS(bookingId, customerPhone, messageContent);
        sendWhatsApp(bookingId, customerPhone, messageContent);
    }

    /**
     * Finds FAILED notifications with fewer than MAX_RETRIES attempts and re-sends them.
     * Called by NotificationRetryTask every 15 minutes.
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
            String from = notificationLog.getNotificationType() == NotificationType.WHATSAPP
                    ? twilioConfig.getWhatsappNumber()
                    : twilioConfig.getPhoneNumber();
            boolean whatsapp = notificationLog.getNotificationType() == NotificationType.WHATSAPP;
            attemptSend(notificationLog, notificationLog.getCustomerPhone(),
                    notificationLog.getMessageContent(), from, whatsapp);
        }
    }

    /**
     * Formats an Indian phone number to Twilio's E.164 form: +91XXXXXXXXXX.
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

    private void attemptSend(NotificationLog notificationLog, String toPhone, String messageContent,
                              String fromNumber, boolean whatsapp) {
        try {
            PhoneNumber to = new PhoneNumber(whatsapp ? "whatsapp:" + toPhone : toPhone);
            PhoneNumber from = new PhoneNumber(fromNumber);

            Message message = Message.creator(to, from, messageContent).create();

            notificationLog.setStatus(NotificationStatus.SENT);
            notificationLog.setTwilioSid(message.getSid());
            notificationLog.setSentAt(LocalDateTime.now());
            notificationLog.setErrorMessage(null);
            notificationLogRepository.save(notificationLog);

            log.info("{} sent: bookingId={}, phone={}, sid={}",
                    notificationLog.getNotificationType(), notificationLog.getBookingId(),
                    toPhone, message.getSid());
        } catch (Exception ex) {
            notificationLog.setStatus(NotificationStatus.FAILED);
            notificationLog.setErrorMessage(ex.getMessage());
            notificationLogRepository.save(notificationLog);

            log.error("Failed to send {}: bookingId={}, phone={}, error={}",
                    notificationLog.getNotificationType(), notificationLog.getBookingId(),
                    toPhone, ex.getMessage());
            errorLogService.logServiceError(ex, "NotificationLog", notificationLog.getId(), null);
        }
    }
}
