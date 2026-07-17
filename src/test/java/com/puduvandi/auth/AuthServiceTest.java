package com.puduvandi.auth;

import com.puduvandi.auth.dto.AuthTokenResponse;
import com.puduvandi.auth.dto.RefreshTokenRequest;
import com.puduvandi.auth.dto.SendOtpRequest;
import com.puduvandi.auth.dto.VerifyOtpRequest;
import com.puduvandi.auth.entity.OtpRecord;
import com.puduvandi.auth.entity.RefreshToken;
import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.OtpRecordRepository;
import com.puduvandi.auth.repository.RefreshTokenRepository;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.auth.service.AuthService;
import com.puduvandi.auth.service.RefreshTokenSecurityService;
import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import com.puduvandi.config.JwtProperties;
import com.puduvandi.config.OtpProperties;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.UnauthorizedException;
import com.puduvandi.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OtpRecordRepository otpRecordRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private JwtProperties jwtProperties;
    @Mock private OtpProperties otpProperties;
    @Mock private com.puduvandi.notification.service.NotificationService notificationService;
    @Mock private RefreshTokenSecurityService refreshTokenSecurityService;

    @InjectMocks
    private AuthService authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(1L)
                .phoneNumber("9876543210")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_SUBMITTED)
                .deleted(false)
                .build();
    }

    // ===== sendOtp Tests =====

    @Test
    @DisplayName("sendOtp: should create new user if not exists")
    void sendOtp_newUser_shouldRegisterAndSaveOtp() {
        SendOtpRequest request = new SendOtpRequest("9876543210");

        when(userRepository.findByPhoneNumber("9876543210")).thenReturn(Optional.empty());
        when(otpProperties.isMockEnabled()).thenReturn(true);
        when(otpProperties.getMockOtp()).thenReturn("123456");
        when(otpProperties.getExpiryMinutes()).thenReturn(5);

        authService.sendOtp(request);

        verify(userRepository).save(any(User.class));
        verify(otpRecordRepository).save(any(OtpRecord.class));
    }

    @Test
    @DisplayName("sendOtp: should NOT create/modify user if already exists and active")
    void sendOtp_existingUser_shouldNotCreateDuplicate() {
        SendOtpRequest request = new SendOtpRequest("9876543210");

        when(userRepository.findByPhoneNumber("9876543210")).thenReturn(Optional.of(activeUser));
        when(otpProperties.isMockEnabled()).thenReturn(true);
        when(otpProperties.getMockOtp()).thenReturn("123456");
        when(otpProperties.getExpiryMinutes()).thenReturn(5);

        authService.sendOtp(request);

        verify(userRepository, never()).save(any(User.class));
        verify(otpRecordRepository).save(any(OtpRecord.class));
    }

    @Test
    @DisplayName("sendOtp: soft-deleted user with same phone number is reactivated, not duplicated")
    void sendOtp_softDeletedUser_shouldReactivate() {
        SendOtpRequest request = new SendOtpRequest("9876543210");
        User deletedUser = User.builder()
                .id(2L)
                .phoneNumber("9876543210")
                .role(UserRole.CUSTOMER)
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.APPROVED)
                .deleted(true)
                .build();

        when(userRepository.findByPhoneNumber("9876543210")).thenReturn(Optional.of(deletedUser));
        when(otpProperties.isMockEnabled()).thenReturn(true);
        when(otpProperties.getMockOtp()).thenReturn("123456");
        when(otpProperties.getExpiryMinutes()).thenReturn(5);

        authService.sendOtp(request);

        verify(userRepository).save(argThat(u ->
                !u.isDeleted()
                && u.getRole() == null
                && u.getStatus() == UserStatus.PENDING_VERIFICATION
                && u.getKycStatus() == KycStatus.NOT_SUBMITTED));
        verify(otpRecordRepository).save(any(OtpRecord.class));
    }

    // ===== verifyOtp Tests =====

    @Test
    @DisplayName("verifyOtp: correct OTP should return tokens")
    void verifyOtp_validOtp_shouldReturnTokens() {
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "123456");

        OtpRecord validOtp = OtpRecord.builder()
                .otpCode("123456")
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .build();

        RefreshToken savedToken = RefreshToken.builder()
                .token("refresh-uuid")
                .user(activeUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(userRepository.findByPhoneNumberAndDeletedFalse("9876543210"))
                .thenReturn(Optional.of(activeUser));
        when(otpRecordRepository.findLatestValidOtp(eq("9876543210"), any()))
                .thenReturn(Optional.of(validOtp));
        when(jwtUtil.generateAccessToken(1L, "9876543210", "CUSTOMER"))
                .thenReturn("access-token-xyz");
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenReturn(savedToken);
        when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604800000L);

        AuthTokenResponse response = authService.verifyOtp(request);

        assertThat(response.accessToken()).isEqualTo("access-token-xyz");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().phoneNumber()).isEqualTo("9876543210");
    }

    @Test
    @DisplayName("verifyOtp: wrong OTP should throw UnauthorizedException")
    void verifyOtp_wrongOtp_shouldThrow() {
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "999999");

        OtpRecord validOtp = OtpRecord.builder()
                .otpCode("123456")
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(4))
                .build();

        when(userRepository.findByPhoneNumberAndDeletedFalse("9876543210"))
                .thenReturn(Optional.of(activeUser));
        when(otpRecordRepository.findLatestValidOtp(eq("9876543210"), any()))
                .thenReturn(Optional.of(validOtp));

        assertThatThrownBy(() -> authService.verifyOtp(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Incorrect OTP");
    }

    @Test
    @DisplayName("verifyOtp: suspended user should throw BusinessException")
    void verifyOtp_suspendedUser_shouldThrow() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        VerifyOtpRequest request = new VerifyOtpRequest("9876543210", "123456");

        when(userRepository.findByPhoneNumberAndDeletedFalse("9876543210"))
                .thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> authService.verifyOtp(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("suspended");
    }

    // ===== logout Tests =====

    @Test
    @DisplayName("logout: should revoke all refresh tokens")
    void logout_shouldRevokeAllTokens() {
        authService.logout(1L);
        verify(refreshTokenRepository).revokeAllByUserId(1L);
    }

    // ===== refreshToken Tests =====

    @Test
    @DisplayName("refreshToken: valid token should rotate — old revoked, new one issued")
    void refreshToken_validToken_shouldRotate() {
        RefreshTokenRequest request = new RefreshTokenRequest("old-refresh-token");
        RefreshToken storedToken = RefreshToken.builder()
                .id(10L)
                .token("old-refresh-token")
                .user(activeUser)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .build();

        when(refreshTokenRepository.findByToken("old-refresh-token")).thenReturn(Optional.of(storedToken));
        when(jwtUtil.generateAccessToken(1L, "9876543210", "CUSTOMER")).thenReturn("new-access-token");
        when(jwtProperties.getRefreshTokenExpiryMs()).thenReturn(604800000L);

        AuthTokenResponse response = authService.refreshToken(request);

        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(response.refreshToken()).isNotEqualTo("old-refresh-token");
        assertThat(storedToken.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedToken);
        // one save() for revoking the old token, one for persisting the new rotated token
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        verify(refreshTokenSecurityService, never()).revokeAllForUserIndependently(anyLong());
    }

    @Test
    @DisplayName("refreshToken: reused (already-revoked) token should revoke all sessions and throw")
    void refreshToken_reusedRevokedToken_shouldRevokeAllAndThrow() {
        RefreshTokenRequest request = new RefreshTokenRequest("stolen-refresh-token");
        RefreshToken storedToken = RefreshToken.builder()
                .id(11L)
                .token("stolen-refresh-token")
                .user(activeUser)
                .revoked(true)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .build();

        when(refreshTokenRepository.findByToken("stolen-refresh-token")).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("revoked");

        verify(refreshTokenSecurityService).revokeAllForUserIndependently(1L);
        // rotation must NOT proceed once reuse is detected
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("refreshToken: expired (never revoked) token should throw without triggering reuse revocation")
    void refreshToken_expiredToken_shouldThrow() {
        RefreshTokenRequest request = new RefreshTokenRequest("expired-refresh-token");
        RefreshToken storedToken = RefreshToken.builder()
                .id(12L)
                .token("expired-refresh-token")
                .user(activeUser)
                .revoked(false)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(refreshTokenRepository.findByToken("expired-refresh-token")).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("expired");

        verify(refreshTokenSecurityService, never()).revokeAllForUserIndependently(anyLong());
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("refreshToken: unknown token value should throw UnauthorizedException")
    void refreshToken_unknownToken_shouldThrow() {
        RefreshTokenRequest request = new RefreshTokenRequest("does-not-exist");
        when(refreshTokenRepository.findByToken("does-not-exist")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("refreshToken: suspended user should throw even with a valid token")
    void refreshToken_suspendedUser_shouldThrow() {
        activeUser.setStatus(UserStatus.SUSPENDED);
        RefreshTokenRequest request = new RefreshTokenRequest("valid-token-suspended-user");
        RefreshToken storedToken = RefreshToken.builder()
                .id(13L)
                .token("valid-token-suspended-user")
                .user(activeUser)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusDays(2))
                .build();

        when(refreshTokenRepository.findByToken("valid-token-suspended-user")).thenReturn(Optional.of(storedToken));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("no longer active");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }
}
