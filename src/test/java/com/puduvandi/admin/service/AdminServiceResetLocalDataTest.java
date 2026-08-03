package com.puduvandi.admin.service;

import com.puduvandi.admin.dto.AdminDataResetResponse;
import com.puduvandi.admin.repository.CommissionSettingsRepository;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.bike.repository.BikeRepository;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.delivery.repository.DeliveryOrderRepository;
import com.puduvandi.delivery.repository.DeliverySettingsRepository;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.owner.repository.OwnerDocumentRepository;
import com.puduvandi.owner.repository.OwnerProfileRepository;
import com.puduvandi.partner.repository.PartnerDocumentRepository;
import com.puduvandi.partner.repository.PartnerProfileRepository;
import com.puduvandi.review.repository.ReviewRepository;
import com.puduvandi.user.repository.PhoneChangeRequestRepository;
import com.puduvandi.user.repository.UserDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Regression tests for the destructive local-data-reset endpoint — previously
 * had zero test coverage despite wiping almost the entire schema. Covers the
 * three gates (env, confirmation phrase, and the actual wipe) independently.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService resetLocalData Unit Tests")
class AdminServiceResetLocalDataTest {

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
    @Mock private ReviewRepository reviewRepository;
    @Mock private DeliveryOrderRepository deliveryOrderRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private com.puduvandi.realtime.RealtimeEventPublisher realtimeEventPublisher;
    @Mock private com.puduvandi.audit.service.AdminAuditService adminAuditService;

    private AdminService adminService;

    private static final String CONFIRMATION_PHRASE = "RESET_ALL_DATA";

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, ownerProfileRepository, partnerProfileRepository,
                bikeRepository, bookingRepository, commissionSettingsRepository,
                deliverySettingsRepository, userDocumentRepository, ownerDocumentRepository,
                phoneChangeRequestRepository, partnerDocumentRepository, reviewRepository,
                deliveryOrderRepository, jdbcTemplate, realtimeEventPublisher, adminAuditService);
    }

    private void setEnv(String value) {
        ReflectionTestUtils.setField(adminService, "puduvandiEnv", value);
    }

    @Test
    @DisplayName("unset PUDUVANDI_ENV is disallowed (fail-closed), not treated as local")
    void resetLocalData_unsetEnv_isBlocked() {
        setEnv(null);

        assertThatThrownBy(() -> adminService.resetLocalData(CONFIRMATION_PHRASE))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("disabled outside local");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("blank PUDUVANDI_ENV is disallowed (fail-closed)")
    void resetLocalData_blankEnv_isBlocked() {
        setEnv("   ");

        assertThatThrownBy(() -> adminService.resetLocalData(CONFIRMATION_PHRASE))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("production PUDUVANDI_ENV is disallowed")
    void resetLocalData_productionEnv_isBlocked() {
        setEnv("production");

        assertThatThrownBy(() -> adminService.resetLocalData(CONFIRMATION_PHRASE))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("disabled outside local");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("staging env + correct phrase: allowed, truncates and deletes non-admin users")
    void resetLocalData_stagingEnv_isAllowed() {
        setEnv("staging");
        when(jdbcTemplate.update(anyString())).thenReturn(3);

        AdminDataResetResponse response = adminService.resetLocalData(CONFIRMATION_PHRASE);

        assertThat(response.nonAdminUsersRemoved()).isEqualTo(3);
        assertThat(response.environment()).isEqualTo("staging");
        verify(jdbcTemplate).execute(anyString());
        verify(jdbcTemplate).update(contains("DELETE FROM users WHERE role NOT IN ('ADMIN', 'SUPER_ADMIN')"));
    }

    @Test
    @DisplayName("local env but wrong confirmation phrase is rejected before any DB call")
    void resetLocalData_wrongConfirmationPhrase_isRejected() {
        setEnv("local");

        assertThatThrownBy(() -> adminService.resetLocalData("nope"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Confirmation phrase mismatch");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("local env + correct phrase: truncates and deletes non-admin users, keeps admins")
    void resetLocalData_validRequest_wipesDataAndKeepsAdmins() {
        setEnv("local");
        when(jdbcTemplate.update(anyString())).thenReturn(7);

        AdminDataResetResponse response = adminService.resetLocalData(CONFIRMATION_PHRASE);

        assertThat(response.nonAdminUsersRemoved()).isEqualTo(7);
        assertThat(response.environment()).isEqualTo("local");
        verify(jdbcTemplate).execute(anyString());
        verify(jdbcTemplate).update(contains("DELETE FROM users WHERE role NOT IN ('ADMIN', 'SUPER_ADMIN')"));
    }

    @Test
    @DisplayName("PUDUVANDI_ENV is case/whitespace-insensitive (\"Local\", \" local \")")
    void resetLocalData_envIsCaseAndWhitespaceInsensitive() {
        setEnv(" Local ");
        when(jdbcTemplate.update(anyString())).thenReturn(0);

        AdminDataResetResponse response = adminService.resetLocalData(CONFIRMATION_PHRASE);

        assertThat(response.nonAdminUsersRemoved()).isEqualTo(0);
        verify(jdbcTemplate).execute(anyString());
        verify(jdbcTemplate).update(anyString());
    }
}
