package com.puduvandi.deposit.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DepositStatus;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.deposit.dto.DepositClaimResponse;
import com.puduvandi.deposit.dto.FileDepositClaimRequest;
import com.puduvandi.deposit.entity.DepositClaim;
import com.puduvandi.deposit.repository.DepositClaimRepository;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DepositClaimService Unit Tests")
class DepositClaimServiceTest {

    @Mock private DepositClaimRepository depositClaimRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentService paymentService;
    @Mock private com.puduvandi.push.service.WebPushService webPushService;

    private DepositClaimService depositClaimService;

    private static final Long OWNER_USER_ID = 5L;
    private static final Long ADMIN_USER_ID = 1L;
    private static final Long BOOKING_ID = 10L;

    private User owner;
    private User admin;

    @BeforeEach
    void setUp() {
        depositClaimService = new DepositClaimService(depositClaimRepository, bookingRepository, userRepository, paymentService, webPushService);
        owner = User.builder().id(OWNER_USER_ID).fullName("Muthu").build();
        admin = User.builder().id(ADMIN_USER_ID).fullName("Admin One").build();
    }

    private Booking completedBooking(DepositStatus depositStatus) {
        return Booking.builder()
                .id(BOOKING_ID)
                .bookingReference("PV-20260717-0010")
                .status(BookingStatus.COMPLETED)
                .securityDeposit(new BigDecimal("500.00"))
                .depositStatus(depositStatus)
                .build();
    }

    // ===== fileClaim =====

    @Test
    @DisplayName("fileClaim: booking not owned by this owner throws ResourceNotFoundException")
    void fileClaim_bookingNotFound_throws() {
        when(bookingRepository.findByIdAndOwner_UserIdAndDeletedFalse(BOOKING_ID, OWNER_USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositClaimService.fileClaim(
                OWNER_USER_ID, BOOKING_ID, new FileDepositClaimRequest(new BigDecimal("100.00"), "Scratched tank", null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(depositClaimRepository, never()).save(any());
    }

    @Test
    @DisplayName("fileClaim: booking not COMPLETED is rejected")
    void fileClaim_notCompleted_throws() {
        Booking booking = completedBooking(DepositStatus.HELD);
        booking.setStatus(BookingStatus.RIDE_STARTED);
        when(bookingRepository.findByIdAndOwner_UserIdAndDeletedFalse(BOOKING_ID, OWNER_USER_ID))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> depositClaimService.fileClaim(
                OWNER_USER_ID, BOOKING_ID, new FileDepositClaimRequest(new BigDecimal("100.00"), "Scratched tank", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("completed");
    }

    @Test
    @DisplayName("fileClaim: deposit not HELD (already claimed/resolved) is rejected")
    void fileClaim_depositNotHeld_throws() {
        Booking booking = completedBooking(DepositStatus.CLAIM_PENDING);
        when(bookingRepository.findByIdAndOwner_UserIdAndDeletedFalse(BOOKING_ID, OWNER_USER_ID))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> depositClaimService.fileClaim(
                OWNER_USER_ID, BOOKING_ID, new FileDepositClaimRequest(new BigDecimal("100.00"), "Scratched tank", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not eligible");
    }

    @Test
    @DisplayName("fileClaim: deduction exceeding the security deposit is rejected")
    void fileClaim_deductionExceedsDeposit_throws() {
        Booking booking = completedBooking(DepositStatus.HELD);
        when(bookingRepository.findByIdAndOwner_UserIdAndDeletedFalse(BOOKING_ID, OWNER_USER_ID))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> depositClaimService.fileClaim(
                OWNER_USER_ID, BOOKING_ID, new FileDepositClaimRequest(new BigDecimal("600.00"), "Totalled it", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed");
    }

    @Test
    @DisplayName("fileClaim: valid claim is saved PENDING and flips the booking to CLAIM_PENDING")
    void fileClaim_valid_savesAndFlipsBooking() {
        Booking booking = completedBooking(DepositStatus.HELD);
        when(bookingRepository.findByIdAndOwner_UserIdAndDeletedFalse(BOOKING_ID, OWNER_USER_ID))
                .thenReturn(Optional.of(booking));
        when(userRepository.getReferenceById(OWNER_USER_ID)).thenReturn(owner);
        when(depositClaimRepository.save(any(DepositClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        DepositClaimResponse response = depositClaimService.fileClaim(
                OWNER_USER_ID, BOOKING_ID, new FileDepositClaimRequest(new BigDecimal("150.00"), "Scratched tank", null));

        assertThat(response.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(response.deductionAmount()).isEqualByComparingTo("150.00");
        assertThat(booking.getDepositStatus()).isEqualTo(DepositStatus.CLAIM_PENDING);
        verify(bookingRepository).save(booking);
    }

    // ===== approveClaim / rejectClaim =====

    @Test
    @DisplayName("approveClaim: unknown claim throws ResourceNotFoundException")
    void approveClaim_unknownClaim_throws() {
        when(depositClaimRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> depositClaimService.approveClaim(ADMIN_USER_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("approveClaim: already-decided claim is rejected")
    void approveClaim_alreadyDecided_throws() {
        DepositClaim claim = DepositClaim.builder().id(1L).status(DocumentStatus.APPROVED).build();
        when(depositClaimRepository.findById(1L)).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> depositClaimService.approveClaim(ADMIN_USER_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been decided");

        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("approveClaim: refunds deposit minus the claimed deduction, marks claim APPROVED")
    void approveClaim_valid_refundsRemainderAndApproves() {
        Booking booking = completedBooking(DepositStatus.CLAIM_PENDING);
        DepositClaim claim = DepositClaim.builder()
                .id(1L)
                .booking(booking)
                .filedByOwner(owner)
                .deductionAmount(new BigDecimal("150.00"))
                .status(DocumentStatus.PENDING)
                .build();
        when(depositClaimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(userRepository.getReferenceById(ADMIN_USER_ID)).thenReturn(admin);
        when(depositClaimRepository.save(any(DepositClaim.class))).thenAnswer(inv -> inv.getArgument(0));

        DepositClaimResponse response = depositClaimService.approveClaim(ADMIN_USER_ID, 1L);

        assertThat(response.status()).isEqualTo(DocumentStatus.APPROVED);
        verify(paymentService).refundDeposit(booking, new BigDecimal("350.00")); // 500 - 150
    }

    @Test
    @DisplayName("rejectClaim: refunds the full deposit, marks claim REJECTED with reason")
    void rejectClaim_valid_refundsFullAndRejects() {
        Booking booking = completedBooking(DepositStatus.CLAIM_PENDING);
        DepositClaim claim = DepositClaim.builder()
                .id(1L)
                .booking(booking)
                .filedByOwner(owner)
                .deductionAmount(new BigDecimal("150.00"))
                .status(DocumentStatus.PENDING)
                .build();
        when(depositClaimRepository.findById(1L)).thenReturn(Optional.of(claim));
        when(userRepository.getReferenceById(ADMIN_USER_ID)).thenReturn(admin);
        ArgumentCaptor<DepositClaim> captor = ArgumentCaptor.forClass(DepositClaim.class);
        when(depositClaimRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        DepositClaimResponse response = depositClaimService.rejectClaim(ADMIN_USER_ID, 1L, "Normal wear and tear");

        assertThat(response.status()).isEqualTo(DocumentStatus.REJECTED);
        assertThat(response.adminRejectionReason()).isEqualTo("Normal wear and tear");
        verify(paymentService).refundDeposit(booking, new BigDecimal("500.00"));
        assertThat(captor.getValue().getAdminRejectionReason()).isEqualTo("Normal wear and tear");
    }

    // ===== listClaims =====

    @Test
    @DisplayName("listClaims: delegates to the repository with the given status filter")
    void listClaims_delegatesToRepository() {
        when(depositClaimRepository.findAllForAdmin(eq(DocumentStatus.PENDING), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        depositClaimService.listClaims(DocumentStatus.PENDING, 0, 20);

        verify(depositClaimRepository).findAllForAdmin(eq(DocumentStatus.PENDING), any());
    }
}
