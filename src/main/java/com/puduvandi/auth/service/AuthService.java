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
import com.puduvandi.exception.ConflictException;
import com.puduvandi.notification.service.NotificationService;
import com.puduvandi.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
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
    private final NotificationService notificationService;
    private final RefreshTokenSecurityService refreshTokenSecurityService;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generates and sends an OTP to the given phone number.
     * mock-enabled=true: code is always mockOtp and only logged (no SMS sent).
     * mock-enabled=false: a real random code is generated and sent via SMS.
     * <p>
     * Deliberately does NOT touch the users table — merely requesting an OTP
     * (a typo'd number, an abandoned signup) must never reserve a phone
     * number. The account only gets created once the OTP is actually proven
     * correct, in verifyOtp() below.
     */
    @Transactional
    public void sendOtp(SendOtpRequest request) {
        String otpCode = generateOtp();

        OtpRecord otpRecord = OtpRecord.builder()
                .phoneNumber(request.phoneNumber())
                .otpCode(otpCode)
                .purpose("LOGIN")
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(otpProperties.getExpiryMinutes()))
                .build();

        otpRecordRepository.save(otpRecord);

        if (otpProperties.isMockEnabled()) {
            log.info("OTP for {} : {} (mock=true)", request.phoneNumber(), otpCode);
        } else {
            notificationService.sendSMS(null, request.phoneNumber(),
                    "Your Puduvandi login OTP is " + otpCode + ". Valid for "
                            + otpProperties.getExpiryMinutes() + " minutes. Do not share this with anyone.");
            log.info("OTP SMS queued for {}", request.phoneNumber());
        }
    }

    /**
     * Verifies the OTP and issues JWT access + refresh tokens.
     * Returns isNewUser=true when the user has no role yet (first login).
     * <p>
     * The user account itself is created (or reactivated, if soft-deleted)
     * right here — only once the OTP is proven correct, never earlier in
     * sendOtp(). This is the single point where a phone number is actually
     * reserved.
     */
    @Transactional
    public AuthTokenResponse verifyOtp(VerifyOtpRequest request) {
        OtpRecord otpRecord = otpRecordRepository
                .findLatestValidOtp(request.phoneNumber(), LocalDateTime.now())
                .orElseThrow(() -> new UnauthorizedException("OTP has expired or is invalid."));

        if (!otpRecord.getOtpCode().equals(request.otp())) {
            throw new UnauthorizedException("Incorrect OTP. Please try again.");
        }

        otpRecordRepository.markAllUsedByPhone(request.phoneNumber());

        User user = userRepository.findByPhoneNumber(request.phoneNumber())
                .map(existing -> {
                    if (!existing.isDeleted()) return existing;
                    // The phone_number column is globally unique, so a soft-deleted
                    // account must be reactivated rather than inserting a new row.
                    existing.setDeleted(false);
                    existing.setRole(null);
                    existing.setStatus(UserStatus.ACTIVE);
                    existing.setKycStatus(KycStatus.NOT_SUBMITTED);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User created = userRepository.save(User.builder()
                            .phoneNumber(request.phoneNumber())
                            .role(null)  // set via /auth/set-role after first login
                            .status(UserStatus.ACTIVE)
                            .kycStatus(KycStatus.NOT_SUBMITTED)
                            .deleted(false)
                            .build());
                    log.info("New user registered: phone={}", request.phoneNumber());
                    return created;
                });

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException("Your account has been suspended. Please contact support.");
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
                new UserSummary(user.getId(), user.getPhoneNumber(), user.getEmail(), user.getFullName(),
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
        String accessToken = jwtUtil.generateAccessToken(user.getId(), loginIdentifier(user), role.name());
        String refreshToken = createAndSaveRefreshToken(user);

        log.info("Role set for userId={}: role={}", userId, role);

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                new UserSummary(user.getId(), user.getPhoneNumber(), user.getEmail(), user.getFullName(),
                        role, user.getStatus(), false, profileComplete)
        );
    }

    /**
     * Issues a new access token AND rotates the refresh token using a valid refresh token.
     * The presented refresh token is revoked and a brand-new one is issued in its place.
     * <p>
     * Reuse detection: if the presented token is found but was already revoked (as opposed
     * to merely expired), that's a strong signal the token was stolen and used after the
     * legitimate owner already rotated it — every refresh token for the user is revoked
     * to kill the session everywhere.
     */
    @Transactional
    public AuthTokenResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token."));

        if (storedToken.isRevoked()) {
            // Reuse of an already-rotated/revoked token — possible token theft.
            // Revoked in its own transaction (REQUIRES_NEW) so it isn't undone
            // by the rollback that throwing UnauthorizedException triggers below.
            Long stolenUserId = storedToken.getUser().getId();
            refreshTokenSecurityService.revokeAllForUserIndependently(stolenUserId);
            log.warn("Refresh token reuse detected for userId={} — all sessions revoked.", stolenUserId);
            throw new UnauthorizedException("Refresh token has been revoked. Please login again.");
        }

        if (storedToken.isExpiredOrRevoked()) {
            throw new UnauthorizedException("Refresh token has expired. Please login again.");
        }

        User user = storedToken.getUser();

        if (user.isDeleted() || user.getStatus() == UserStatus.SUSPENDED) {
            throw new UnauthorizedException("Account is no longer active. Please contact support.");
        }

        // Rotate: revoke the presented token and issue a brand-new one.
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);
        String newRefreshToken = createAndSaveRefreshToken(user);

        String roleStr = (user.getRole() != null) ? user.getRole().name() : "NEW_USER";
        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), loginIdentifier(user), roleStr);

        return new AuthTokenResponse(
                newAccessToken,
                newRefreshToken,
                new UserSummary(user.getId(), user.getPhoneNumber(), user.getEmail(), user.getFullName(),
                        user.getRole(), user.getStatus(), user.getRole() == null, isProfileComplete(user))
        );
    }

    /**
     * Creates a new account with email + password. Mirrors sendOtp+verifyOtp's
     * "new user" shape: role is left null so the frontend routes straight to
     * the same role-selection screen (/auth/set-role) used by the phone flow.
     */
    @Transactional
    public AuthTokenResponse emailSignup(EmailSignupRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(null)
                .status(UserStatus.ACTIVE)
                .kycStatus(KycStatus.NOT_SUBMITTED)
                .deleted(false)
                .build();
        userRepository.save(user);
        log.info("New user registered via email: email={}", email);

        String accessToken  = jwtUtil.generateAccessToken(user.getId(), email, "NEW_USER");
        String refreshToken = createAndSaveRefreshToken(user);

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                new UserSummary(user.getId(), null, email, user.getFullName(),
                        null, user.getStatus(), true, false)
        );
    }

    /**
     * Logs in with email + password. Mirrors verifyOtp's response shape so the
     * frontend's existing login() handling works unchanged for either flow.
     */
    @Transactional
    public AuthTokenResponse emailLogin(EmailLoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCaseAndDeletedFalse(email)
                .orElseThrow(() -> new UnauthorizedException("Incorrect email or password."));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Incorrect email or password.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException("Your account has been suspended. Please contact support.");
        }

        boolean isNewUser = (user.getRole() == null);
        boolean profileComplete = isProfileComplete(user);

        String roleStr = isNewUser ? "NEW_USER" : user.getRole().name();
        String accessToken  = jwtUtil.generateAccessToken(user.getId(), email, roleStr);
        String refreshToken = createAndSaveRefreshToken(user);

        log.info("User logged in via email: id={}, role={}, isNewUser={}", user.getId(), roleStr, isNewUser);

        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                new UserSummary(user.getId(), user.getPhoneNumber(), email, user.getFullName(),
                        user.getRole(), user.getStatus(), isNewUser, profileComplete)
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

    // The JWT subject just needs to be *some* stable identifier — resolution
    // on every request is by the userId claim (see JwtAuthenticationFilter),
    // never by looking this back up — so an email-only account can use its
    // email here in place of a phone number.
    private String loginIdentifier(User user) {
        return user.getPhoneNumber() != null ? user.getPhoneNumber() : user.getEmail();
    }

    private String generateOtp() {
        if (otpProperties.isMockEnabled()) {
            return otpProperties.getMockOtp();
        }
        int bound = (int) Math.pow(10, otpProperties.getLength());
        return String.format("%0" + otpProperties.getLength() + "d", SECURE_RANDOM.nextInt(bound));
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
