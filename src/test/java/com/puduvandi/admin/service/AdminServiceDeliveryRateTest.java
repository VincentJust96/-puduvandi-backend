package com.puduvandi.admin.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.bike.repository.BikeRepository;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.admin.repository.CommissionSettingsRepository;
import com.puduvandi.delivery.dto.DeliveryRateResponse;
import com.puduvandi.delivery.dto.UpdateDeliveryRateRequest;
import com.puduvandi.delivery.entity.DeliverySettings;
import com.puduvandi.delivery.repository.DeliverySettingsRepository;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.owner.repository.OwnerDocumentRepository;
import com.puduvandi.owner.repository.OwnerProfileRepository;
import com.puduvandi.partner.repository.PartnerDocumentRepository;
import com.puduvandi.partner.repository.PartnerProfileRepository;
import com.puduvandi.user.repository.PhoneChangeRequestRepository;
import com.puduvandi.user.repository.UserDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Focused regression tests for the delivery-rate self-healing fix:
 * updateDeliveryRate() must be able to create a new active DeliverySettings
 * row when none exists (admin recovery path), while getActiveDeliveryRate()
 * (read-only display) must still surface a 404 when unconfigured.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService delivery-rate Unit Tests")
class AdminServiceDeliveryRateTest {

    @Mock private UserRepository userRepository;
    @Mock private OwnerProfileRepository ownerProfileRepository;
    @Mock private PartnerProfileRepository partnerProfileRepository;
    @Mock private BikeRepository bikeRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private CommissionSettingsRepository commissionSettingsRepository;
    @Mock private DeliverySettingsRepository deliverySettingsRepository;
    @Mock private UserDocumentRepository userDocumentRepository;
    @Mock private OwnerDocumentRepository ownerDocumentRepository;
    @Mock private PhoneChangeRequestRepository phoneChangeRequestRepository;
    @Mock private PartnerDocumentRepository partnerDocumentRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private AdminService adminService;

    private static final Long ADMIN_ID = 1L;
    private User admin;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, ownerProfileRepository, partnerProfileRepository,
                bikeRepository, bookingRepository, commissionSettingsRepository,
                deliverySettingsRepository, userDocumentRepository, ownerDocumentRepository,
                phoneChangeRequestRepository, partnerDocumentRepository, jdbcTemplate);
        admin = User.builder().id(ADMIN_ID).fullName("Admin One").build();
    }

    @Test
    @DisplayName("updateDeliveryRate: active row exists -> updates it in place")
    void updateDeliveryRate_existingActiveRow_updatesInPlace() {
        DeliverySettings existing = DeliverySettings.builder().id(2L).ratePerKm(new BigDecimal("15.00")).active(true).build();
        when(deliverySettingsRepository.findTopByActiveTrueOrderByIdDesc()).thenReturn(Optional.of(existing));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(deliverySettingsRepository.save(any(DeliverySettings.class))).thenAnswer(inv -> inv.getArgument(0));

        DeliveryRateResponse response = adminService.updateDeliveryRate(ADMIN_ID, new UpdateDeliveryRateRequest(new BigDecimal("20.00")));

        assertThat(response.ratePerKm()).isEqualByComparingTo("20.00");
        assertThat(response.id()).isEqualTo(2L);
        ArgumentCaptor<DeliverySettings> captor = ArgumentCaptor.forClass(DeliverySettings.class);
        verify(deliverySettingsRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getUpdatedByAdmin()).isEqualTo(admin);
    }

    @Test
    @DisplayName("updateDeliveryRate: no active row (missing/deleted) -> self-heals by creating a new active row instead of throwing")
    void updateDeliveryRate_noActiveRow_createsNewRow() {
        when(deliverySettingsRepository.findTopByActiveTrueOrderByIdDesc()).thenReturn(Optional.empty());
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(deliverySettingsRepository.save(any(DeliverySettings.class)))
                .thenAnswer(inv -> {
                    DeliverySettings s = inv.getArgument(0);
                    s.setId(99L);
                    return s;
                });

        DeliveryRateResponse response = adminService.updateDeliveryRate(ADMIN_ID, new UpdateDeliveryRateRequest(new BigDecimal("18.50")));

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.ratePerKm()).isEqualByComparingTo("18.50");
        ArgumentCaptor<DeliverySettings> captor = ArgumentCaptor.forClass(DeliverySettings.class);
        verify(deliverySettingsRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    @DisplayName("updateDeliveryRate: unknown admin user throws ResourceNotFoundException, no save attempted")
    void updateDeliveryRate_unknownAdmin_throws() {
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.updateDeliveryRate(ADMIN_ID, new UpdateDeliveryRateRequest(new BigDecimal("18.50"))))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(deliverySettingsRepository, never()).save(any());
    }

    @Test
    @DisplayName("getActiveDeliveryRate: no active row -> still throws ResourceNotFoundException (read-only display, no self-heal)")
    void getActiveDeliveryRate_noActiveRow_throws() {
        when(deliverySettingsRepository.findTopByActiveTrueOrderByIdDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getActiveDeliveryRate())
                .isInstanceOf(ResourceNotFoundException.class);

        verify(deliverySettingsRepository, never()).save(any());
    }

    @Test
    @DisplayName("getActiveDeliveryRate: active row exists -> returns it")
    void getActiveDeliveryRate_existingRow_returnsIt() {
        DeliverySettings existing = DeliverySettings.builder().id(2L).ratePerKm(new BigDecimal("15.00")).active(true).build();
        when(deliverySettingsRepository.findTopByActiveTrueOrderByIdDesc()).thenReturn(Optional.of(existing));

        DeliveryRateResponse response = adminService.getActiveDeliveryRate();

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.ratePerKm()).isEqualByComparingTo("15.00");
    }
}
