package com.puduvandi.auth;

import com.puduvandi.auth.dto.AuthTokenResponse;
import com.puduvandi.auth.dto.SendOtpRequest;
import com.puduvandi.auth.dto.VerifyOtpRequest;
import com.puduvandi.auth.entity.OtpRecord;
import com.puduvandi.auth.entity.RefreshToken;
import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.OtpRecordRepository;
import com.puduvandi.auth.repository.RefreshTokenRepository;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.auth.service.AuthService;
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

        when(userRepository.existsByPhoneNumber("9876543210")).thenReturn(false);
        when(otpProperties.isMockEnabled()).thenReturn(true);
        when(otpProperties.getMockOtp()).thenReturn("123456");
        when(otpProperties.getExpiryMinutes()).thenReturn(5);

        authService.sendOtp(request);

        verify(userRepository).save(any(User.class));
        verify(otpRecordRepository).save(any(OtpRecord.class));
    }

    @Test
    @DisplayName("sendOtp: should NOT create user if already exists")
    void sendOtp_existingUser_shouldNotCreateDuplicate() {
        SendOtpRequest request = new SendOtpRequest("9876543210");

        when(userRepository.existsByPhoneNumber("9876543210")).thenReturn(true);
        when(otpProperties.isMockEnabled()).thenReturn(true);
        when(otpProperties.getMockOtp()).thenReturn("123456");
        when(otpProperties.getExpiryMinutes()).thenReturn(5);

        authService.sendOtp(request);

        verify(userRepository, never()).save(any(User.class));
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
}
