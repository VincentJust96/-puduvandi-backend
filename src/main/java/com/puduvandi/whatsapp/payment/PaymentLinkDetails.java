package com.puduvandi.whatsapp.payment;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What the payment page shows. Deliberately no customer name or phone number:
 * anyone holding the link sees this.
 *
 * @param status PAYABLE (can pay now), PAID (done — link used up) or EXPIRED
 */
public record PaymentLinkDetails(
    String status,
    String bookingReference,
    String bike,
    String area,
    LocalDateTime pickupDatetime,
    LocalDateTime returnDatetime,
    BigDecimal rentAmount,
    BigDecimal securityDeposit,
    BigDecimal totalAmount,
    LocalDateTime expiresAt
) {}
