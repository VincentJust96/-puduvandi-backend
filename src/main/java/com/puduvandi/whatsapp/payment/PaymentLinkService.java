package com.puduvandi.whatsapp.payment;

import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.PaymentType;
import com.puduvandi.config.JwtProperties;
import com.puduvandi.config.RazorpayConfig;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.payment.dto.PaymentOrderResponse;
import com.puduvandi.payment.service.PaymentService;
import com.puduvandi.whatsapp.config.WhatsAppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * No-login payment links for WhatsApp customers: {@code <id>.<nonce>.<signature>}, where the
 * signature is an HMAC over id + nonce. A link can pay only its own booking, only until it
 * expires (the booking's payment window), and only once — it is marked used as soon as the
 * payment verifies. Confirming the booking fires the normal booking confirmation, which
 * NotificationService delivers on WhatsApp.
 * <p>
 * Not @Transactional as a whole, for the same reason as the web flow: PaymentService saves a
 * FAILED payment and then throws, and a shared transaction would roll that record back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentLinkService {

    public static final String STATUS_PAYABLE = "PAYABLE";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private static final String INVALID = "This payment link is not valid. Send HI to Puduvandi on WhatsApp to book again.";
    private static final String KEY_LABEL = "puduvandi-whatsapp-payment-link-v1";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PaymentLinkRepository linkRepository;
    private final BookingRepository bookingRepository;
    private final PaymentService paymentService;
    private final WhatsAppProperties properties;
    private final RazorpayConfig razorpayConfig;
    private final JwtProperties jwtProperties;

    /** @return the full URL to send the customer */
    public String createLink(Long bookingId) {
        byte[] nonceBytes = new byte[24];
        RANDOM.nextBytes(nonceBytes);
        PaymentLink link = linkRepository.save(new PaymentLink(bookingId, B64.encodeToString(nonceBytes),
                LocalDateTime.now().plusMinutes(razorpayConfig.getPaymentExpiryMinutes())));
        String payload = link.getId() + "." + link.getNonce();
        return properties.getPaymentPageUrl() + "/" + payload + "." + sign(payload);
    }

    @Transactional(readOnly = true)
    public PaymentLinkDetails details(String token) {
        PaymentLink link = resolve(token);
        Booking booking = booking(link);
        return new PaymentLinkDetails(status(link, booking), booking.getBookingReference(),
                booking.getBike().getBrand() + " " + booking.getBike().getModel(), booking.getBike().getArea(),
                booking.getPickupDatetime(), booking.getReturnDatetime(),
                booking.getBaseAmount(), booking.getSecurityDeposit(), booking.getTotalAmount(), link.getExpiresAt());
    }

    /** Starts (or restarts, after a failed attempt) a Razorpay order for the full amount. */
    public PaymentOrderResponse createOrder(String token) {
        PaymentLink link = resolve(token);
        Booking booking = requirePayable(link);
        return paymentService.createOrder(booking.getCustomer().getId(), List.of(booking.getId()), PaymentType.FULL);
    }

    /** Checks Razorpay's signature, confirms the booking and uses up the link. */
    public void verify(String token, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        PaymentLink link = resolve(token);
        Booking booking = requirePayable(link);
        List<Long> paidBookingIds = paymentService.verifyAndCapture(booking.getCustomer().getId(),
                razorpayOrderId, razorpayPaymentId, razorpaySignature);
        if (!paidBookingIds.contains(booking.getId())) {
            throw new BusinessException("This payment is for a different booking.");
        }
        link.setUsedAt(LocalDateTime.now());
        linkRepository.save(link);
        log.info("WhatsApp payment link used: linkId={}, bookingId={}", link.getId(), booking.getId());
    }

    // ===== Internal =====

    PaymentLink resolve(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(INVALID);
        }
        String payload = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(INVALID);
        }
        long id;
        try {
            id = Long.parseLong(parts[0]);
        } catch (NumberFormatException ex) {
            throw new BusinessException(INVALID);
        }
        PaymentLink link = linkRepository.findById(id).orElseThrow(() -> new BusinessException(INVALID));
        if (!MessageDigest.isEqual(link.getNonce().getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(INVALID);
        }
        return link;
    }

    private Booking requirePayable(PaymentLink link) {
        Booking booking = booking(link);
        String status = status(link, booking);
        if (STATUS_PAID.equals(status)) {
            throw new BusinessException("This booking is already paid ✅");
        }
        if (STATUS_EXPIRED.equals(status)) {
            throw new BusinessException("This payment link has expired. Send HI to Puduvandi on WhatsApp to book again.");
        }
        return booking;
    }

    private static String status(PaymentLink link, Booking booking) {
        if (link.getUsedAt() != null || booking.getStatus() == BookingStatus.CONFIRMED) {
            return STATUS_PAID;
        }
        if (booking.getStatus() == BookingStatus.PAYMENT_PENDING && LocalDateTime.now().isBefore(link.getExpiresAt())) {
            return STATUS_PAYABLE;
        }
        return STATUS_EXPIRED;
    }

    private Booking booking(PaymentLink link) {
        return bookingRepository.findByIdAndDeletedFalse(link.getBookingId())
                .orElseThrow(() -> new BusinessException(INVALID));
    }

    /**
     * HMAC-SHA256 keyed with a key derived from the JWT secret under its own label, so a link
     * signature can never double as a token signature and no extra secret has to be deployed.
     */
    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] key = mac.doFinal(KEY_LABEL.getBytes(StandardCharsets.UTF_8));
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }
}
