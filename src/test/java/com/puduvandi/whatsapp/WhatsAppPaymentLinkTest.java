package com.puduvandi.whatsapp;

import com.puduvandi.auth.entity.User;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.PaymentType;
import com.puduvandi.config.JwtProperties;
import com.puduvandi.config.RazorpayConfig;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.payment.service.PaymentService;
import com.puduvandi.whatsapp.config.WhatsAppProperties;
import com.puduvandi.whatsapp.payment.PaymentLink;
import com.puduvandi.whatsapp.payment.PaymentLinkDetails;
import com.puduvandi.whatsapp.payment.PaymentLinkRepository;
import com.puduvandi.whatsapp.payment.PaymentLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsApp no-login payment links")
class WhatsAppPaymentLinkTest {

    private static final String BASE = "http://localhost:8080/api/v1/pay";

    @Mock private PaymentLinkRepository linkRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentService paymentService;

    private final Map<Long, PaymentLink> links = new HashMap<>();
    private PaymentLinkService service;
    private Booking booking;

    @BeforeEach
    void setUp() {
        WhatsAppProperties props = new WhatsAppProperties();
        props.setPaymentPageUrl(BASE);
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret("test-secret");
        service = new PaymentLinkService(linkRepository, bookingRepository, paymentService, props,
                new RazorpayConfig(), jwt);

        lenient().when(linkRepository.save(any())).thenAnswer(inv -> {
            PaymentLink link = inv.getArgument(0);
            if (link.getId() == null) {
                link.setId((long) links.size() + 1);
            }
            links.put(link.getId(), link);
            return link;
        });
        lenient().when(linkRepository.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(links.get(inv.<Long>getArgument(0))));

        User customer = User.builder().phoneNumber("9876543210").build();
        customer.setId(42L);
        Bike bike = Bike.builder().brand("Honda").model("Activa").area("White Town").build();
        booking = Booking.builder().bookingReference("PV-0001").customer(customer).bike(bike)
                .status(BookingStatus.PAYMENT_PENDING)
                .pickupDatetime(LocalDateTime.now().plusDays(1)).returnDatetime(LocalDateTime.now().plusDays(2))
                .baseAmount(new BigDecimal("400")).securityDeposit(new BigDecimal("500")).totalAmount(new BigDecimal("900"))
                .build();
        booking.setId(7L);
        lenient().when(bookingRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(booking));
    }

    private String newToken() {
        String url = service.createLink(7L);
        assertThat(url).startsWith(BASE + "/");
        return url.substring(BASE.length() + 1);
    }

    @Test
    @DisplayName("a fresh link resolves and shows a payable booking — without customer name or phone")
    void freshLink() {
        String token = newToken();
        assertThat(token.split("\\.")).hasSize(3);

        PaymentLinkDetails details = service.details(token);

        assertThat(details.status()).isEqualTo(PaymentLinkService.STATUS_PAYABLE);
        assertThat(details.bookingReference()).isEqualTo("PV-0001");
        assertThat(details.totalAmount()).isEqualByComparingTo("900");
        assertThat(details.toString()).doesNotContain("9876543210");
    }

    @Test
    @DisplayName("a changed signature, id or nonce is rejected")
    void tamperedLinks() {
        String[] p = newToken().split("\\.");
        newToken(); // link id 2 exists too

        assertThatThrownBy(() -> service.details(p[0] + "." + p[1] + "." + p[2].substring(1) + "A"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.details("2." + p[1] + "." + p[2]))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.details(p[0] + ".x" + p[1] + "." + p[2]))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.details("garbage")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.details(null)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("a validly signed link whose nonce doesn't match the stored one is rejected")
    void nonceMismatch() {
        String token = newToken();
        links.get(1L).setNonce("replaced");

        assertThatThrownBy(() -> service.details(token)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("order is for the full amount, on behalf of the booking's customer")
    void createOrder() {
        String token = newToken();

        service.createOrder(token);

        verify(paymentService).createOrder(42L, List.of(7L), PaymentType.FULL);
    }

    @Test
    @DisplayName("successful payment uses the link up — it can't start another payment")
    void singleUse() {
        String token = newToken();
        when(paymentService.verifyAndCapture(42L, "order_1", "pay_1", "sig")).thenReturn(List.of(7L));

        service.verify(token, "order_1", "pay_1", "sig");

        assertThat(links.get(1L).getUsedAt()).isNotNull();
        assertThat(service.details(token).status()).isEqualTo(PaymentLinkService.STATUS_PAID);
        assertThatThrownBy(() -> service.createOrder(token)).hasMessageContaining("already paid");
        assertThatThrownBy(() -> service.verify(token, "order_1", "pay_1", "sig")).hasMessageContaining("already paid");
        verify(paymentService, times(1)).verifyAndCapture(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a failed signature check leaves the link usable for a retry")
    void failedVerifyKeepsLink() {
        String token = newToken();
        when(paymentService.verifyAndCapture(any(), any(), any(), any()))
                .thenThrow(new BusinessException("Payment could not be verified."));

        assertThatThrownBy(() -> service.verify(token, "order_1", "pay_1", "bad")).isInstanceOf(BusinessException.class);

        assertThat(links.get(1L).getUsedAt()).isNull();
        assertThat(service.details(token).status()).isEqualTo(PaymentLinkService.STATUS_PAYABLE);
    }

    @Test
    @DisplayName("an expired link, or one whose booking was released, can't pay")
    void expired() {
        String token = newToken();
        links.get(1L).setExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertThat(service.details(token).status()).isEqualTo(PaymentLinkService.STATUS_EXPIRED);
        assertThatThrownBy(() -> service.createOrder(token)).hasMessageContaining("expired");

        links.get(1L).setExpiresAt(LocalDateTime.now().plusMinutes(10));
        booking.setStatus(BookingStatus.CANCELLED);
        assertThatThrownBy(() -> service.createOrder(token)).hasMessageContaining("expired");
        verifyNoInteractions(paymentService);
    }
}
