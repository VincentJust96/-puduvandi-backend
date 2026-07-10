package com.puduvandi.auth.service;

import com.puduvandi.auth.dto.*;
import com.puduvandi.auth.entity.OtpRecord;
import com.puduvandi.auth.entity.RefreshToken;
import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.OtpRecordRepository;
import com.puduvandi.auth.repository.RefreshTokenRepository;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.common.enums.UserRole;
import com.puduvandi.common.enums.UserStatus;
import com.puduvandi.config.JwtProperties;
import com.puduvandi.config.OtpProperties;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.exception.UnauthorizedException;
import com.puduvandi.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpRecordRepository otpRecordRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final OtpProperties otpProperties;

    /**
     * Generates and sends (mocked) OTP to the given phone number.
     * Creates a new user with null role if not already registered.
     * The user selects CUSTOMER or OWNER after OTP verification.
     */
    @Transactional
    public void sendOtp(SendOtpRequest request) {
        if (!userRepository.existsByPhoneNumber(request.phoneNumber())) {
            User newUser = User.builder()
                    .phoneNumber(request.phoneNumber())
                    .role(null)  // set via /auth/set-role after first login
                    .status(UserStatus.PENDING_VERIFICATION)
                    .kycStatus(KycStatus.NOT_SUBMITTED)
                    .deleted(false)
                    .build();
            userRepository.save(newUser);
            log.info("New user registered: phone={}", request.phoneNumber());
        }

        String otpCode = generateOtp();

        OtpRecord otpRecord = OtpRecord.builder()
                .phoneNumber(request.phoneNumber())
                .otpCode(otpCode)
                .purpose("LOGIN")
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(otpProperties.getExpiryMinutes()))
                .build();

        otpRecordRepository.save(otpRecord);
        log.info("OTP for {} : {} (mock={})", request.phoneNumber(), otpCode, otpProperties.isMockEnabled());
    }

    /**
     * Verifies the OTP and issues JWT access + refresh tokens.
     * Returns isNewUser=true when the user has no role yet (first login).
     */
    @Transactional
    public AuthTokenResponse verifyOtp(VerifyOtpRequest request) {
        User user = userRepository.findByPhoneNumberAndDeletedFalse(request.phoneNumber())
                .orElseThrow(() -> new BusinessException("User not found. Please request an OTP first."));

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException("Your account has been suspended. Please contact support.");
        }

        OtpRecord otpRecord = otpRecordRepository
                .findLatestValidOtp(request.phoneNumber(), LocalDateTime.now())
                .orElseThrow(() -> new UnauthorizedException("OTP has expired or is invalid."));

        if (!otpRecord.getOtpCode().equals(request.otp())) {
            throw new UnauthorizedException("Incorrect OTP. Please try again.");
        }

        otpRecordRepository.markAllUsedByPhone(request.phoneNumber());

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
        }

        boolean isNewUser = (user.getRole() == null);
        boolean profileComplete = isProfileComplete(user);

        // New users get a "NEW_USER" role claim in the JWT — only grants access to /auth/set-role
        String roleStr = isNewUser ? "NEW_USER" : user.getRole().name();
        String accessToken  = jwtUtil.generateAccessToken(user.getId(), user.getPhoneNumber(), roleStr);
        String refreshToken = createAndSaveRefreshToken(user);

        log.info("User logged in: id={}, phone={}, role={}, isNewUser={}",
                user.getId(), user.getPhoneNumber(), roleStr, isNewUser);

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                new UserSummary(user.getId(), user.getPhoneNumber(), user.getFullName(),
                        user.getRole(), user.getStatus(), isNewUser, profileComplete)
        );
    }

    /**
     * Sets the role for a new user (called once after first OTP verification).
     * Issues a fresh JWT with the chosen role.
     */
    @Transactional
    public AuthTokenResponse setUserRole(Long userId, UserRole role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getRole() != null) {
            throw new BusinessException("Role has already been set and cannot be changed.");
        }

        // Only CUSTOMER and OWNER are valid self-selected roles
        if (role == UserRole.ADMIN) {
            throw new BusinessException("Invalid role selection.");
        }

        user.setRole(role);
        userRepository.save(user);

        boolean profileComplete = isProfileComplete(user);
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getPhoneNumber(), role.name());
        String refreshToken = createAndSaveRefreshToken(user);

        log.info("Role set for userId={}: role={}", userId, role);

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                new UserSummary(user.getId(), user.getPhoneNumber(), user.getFullName(),
                        role, user.getStatus(), false, profileComplete)
        );
    }

    /**
     * Issues a new access token using a valid refresh token.
     */
    @Transactional
    public AuthTokenResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));

        if (storedToken.isExpiredOrRevoked()) {
            throw new UnauthorizedException("Refresh token has expired. Please login again.");
        }

        User user = storedToken.getUser();

        if (user.isDeleted() || user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("Account is no longer active. Please contact support.");
        }

        String roleStr = (user.getRole() != null) ? user.getRole().name() : "NEW_USER";
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getPhoneNumber(), roleStr);

        return new AuthTokenResponse(
                newAccessToken,
                request.refreshToken(),
                new UserSummary(user.getId(), user.getPhoneNumber(), user.getFullName(),
                        user.getRole(), user.getStatus(), user.getRole() == null, isProfileComplete(user))
        );
    }

    /**
     * Revokes all refresh tokens for the user (logout from all devices).
     */
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("User logged out: id={}", userId);
    }

    // ===== Private Helpers =====

    private boolean isProfileComplete(User user) {
        return user.getFullName() != null && !user.getFullName().isBlank();
    }

    private String generateOtp() {
        if (otpProperties.isMockEnabled()) {
            return otpProperties.getMockOtp();
        }
        int bound = (int) Math.pow(10, otpProperties.getLength());
        return String.format("%0" + otpProperties.getLength() + "d", new Random().nextInt(bound));
    }

    private String createAndSaveRefreshToken(User user) {
        String tokenValue = UUID.randomUUID().toString();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(tokenValue)
                .revoked(false)
                .expiresAt(LocalDateTime.now().plusSeconds(
                        jwtProperties.getRefreshTokenExpiryMs() / 1000))
                .build();

        refreshTokenRepository.save(refreshToken);
        return tokenValue;
    }
}
