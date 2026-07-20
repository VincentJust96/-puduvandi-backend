package com.puduvandi.user.service;

import com.puduvandi.auth.entity.OtpRecord;
import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.OtpRecordRepository;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.config.OtpProperties;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ConflictException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.exception.UnauthorizedException;
import com.puduvandi.notification.service.NotificationService;
import com.puduvandi.user.dto.PhoneChangeRequestResponse;
import com.puduvandi.user.dto.UpdateProfileRequest;
import com.puduvandi.user.dto.UploadDocumentRequest;
import com.puduvandi.user.dto.UserDocumentResponse;
import com.puduvandi.user.dto.UserProfileResponse;
import com.puduvandi.user.entity.PhoneChangeRequest;
import com.puduvandi.user.entity.UserDocument;
import com.puduvandi.user.repository.PhoneChangeRequestRepository;
import com.puduvandi.user.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles customer profile viewing, updating, and document uploads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final OtpRecordRepository otpRecordRepository;
    private final OtpProperties otpProperties;
    private final NotificationService notificationService;
    private final PhoneChangeRequestRepository phoneChangeRequestRepository;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String PURPOSE_PHONE_ADD    = "PHONE_ADD";
    private static final String PURPOSE_PHONE_CHANGE = "PHONE_CHANGE";

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = findActiveUser(userId);
        return toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);

        user.setFullName(request.fullName());
        // Email/password accounts have no phone number to fall back on, so
        // this general-purpose profile edit must never null out the one
        // identifier they log in with — only phone-based users (who always
        // have phoneNumber as a fallback) can freely clear their email here.
        if (request.email() != null || user.getPhoneNumber() != null) {
            user.setEmail(request.email());
        }
        user.setProfileImageUrl(request.profileImageUrl());

        User saved = userRepository.save(user);
        log.info("Profile updated for userId={}", userId);
        return toProfileResponse(saved);
    }

    @Transactional
    public UserDocumentResponse uploadDocument(Long userId, UploadDocumentRequest request) {
        User user = findActiveUser(userId);

        userDocumentRepository
                .findByUserIdAndDocumentTypeAndDeletedFalse(userId, request.documentType())
                .ifPresent(existing -> {
                    existing.setDeleted(true);
                    userDocumentRepository.save(existing);
                    log.info("Old {} document replaced for userId={}", request.documentType(), userId);
                });

        UserDocument document = UserDocument.builder()
                .user(user)
                .documentType(request.documentType())
                .documentUrl(request.documentUrl())
                .status(DocumentStatus.PENDING)
                .deleted(false)
                .build();

        UserDocument saved = userDocumentRepository.save(document);
        log.info("Document uploaded: type={}, userId={}", request.documentType(), userId);
        return toDocumentResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UserDocumentResponse> getMyDocuments(Long userId) {
        return userDocumentRepository.findByUserIdAndDeletedFalse(userId)
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    // ===== Phone: add (no existing number — instant, no approval) =====

    @Transactional
    public void sendPhoneAddOtp(Long userId, String phoneNumber) {
        User user = findActiveUser(userId);
        if (user.getPhoneNumber() != null) {
            throw new BusinessException("You already have a phone number on file — use Change instead.");
        }
        ensurePhoneNotTaken(phoneNumber);
        String otp = generateOtp();
        saveOtp(phoneNumber, otp, PURPOSE_PHONE_ADD);
        deliverOtp(phoneNumber, otp);
    }

    @Transactional
    public UserProfileResponse verifyPhoneAddOtp(Long userId, String phoneNumber, String otp) {
        User user = findActiveUser(userId);
        if (user.getPhoneNumber() != null) {
            throw new BusinessException("You already have a phone number on file — use Change instead.");
        }
        consumeOtp(phoneNumber, otp, PURPOSE_PHONE_ADD);
        ensurePhoneNotTaken(phoneNumber);

        user.setPhoneNumber(phoneNumber);
        User saved = userRepository.save(user);
        log.info("Phone number added for userId={}", userId);
        return toProfileResponse(saved);
    }

    // ===== Phone: change (existing number — OTP proves ownership, then admin review) =====

    @Transactional
    public void sendPhoneChangeOtp(Long userId, String newPhoneNumber) {
        findActiveUser(userId);
        ensurePhoneNotTaken(newPhoneNumber);
        String otp = generateOtp();
        saveOtp(newPhoneNumber, otp, PURPOSE_PHONE_CHANGE);
        deliverOtp(newPhoneNumber, otp);
    }

    @Transactional
    public PhoneChangeRequestResponse verifyPhoneChangeOtp(Long userId, String newPhoneNumber, String otp) {
        User user = findActiveUser(userId);
        consumeOtp(newPhoneNumber, otp, PURPOSE_PHONE_CHANGE);
        ensurePhoneNotTaken(newPhoneNumber);

        // A fresh, OTP-verified request supersedes whatever the user asked
        // for previously — only one request should be sitting PENDING per user.
        phoneChangeRequestRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, DocumentStatus.PENDING)
                .ifPresent(existing -> {
                    existing.setStatus(DocumentStatus.REJECTED);
                    existing.setRemarks("Superseded by a newer request.");
                    phoneChangeRequestRepository.save(existing);
                });

        PhoneChangeRequest request = PhoneChangeRequest.builder()
                .user(user)
                .oldPhoneNumber(user.getPhoneNumber())
                .newPhoneNumber(newPhoneNumber)
                .status(DocumentStatus.PENDING)
                .build();
        PhoneChangeRequest saved = phoneChangeRequestRepository.save(request);
        log.info("Phone change requested: userId={}, newPhone={}", userId, newPhoneNumber);
        return toPhoneChangeResponse(saved);
    }

    @Transactional(readOnly = true)
    public PhoneChangeRequestResponse getMyPendingPhoneChangeRequest(Long userId) {
        return phoneChangeRequestRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, DocumentStatus.PENDING)
                .map(this::toPhoneChangeResponse)
                .orElse(null);
    }

    @Transactional
    public void cancelMyPendingPhoneChangeRequest(Long userId) {
        PhoneChangeRequest request = phoneChangeRequestRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, DocumentStatus.PENDING)
                .orElseThrow(() -> new BusinessException("No pending phone-change request to cancel."));
        request.setStatus(DocumentStatus.REJECTED);
        request.setRemarks("Cancelled by user.");
        phoneChangeRequestRepository.save(request);
        log.info("User cancelled their own pending phone-change request: userId={}", userId);
    }

    // ===== Private Helpers =====

    private void ensurePhoneNotTaken(String phoneNumber) {
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new ConflictException("This phone number is already linked to another account.");
        }
    }

    private String generateOtp() {
        if (otpProperties.isMockEnabled()) return otpProperties.getMockOtp();
        int bound = (int) Math.pow(10, otpProperties.getLength());
        return String.format("%0" + otpProperties.getLength() + "d", SECURE_RANDOM.nextInt(bound));
    }

    private void saveOtp(String phoneNumber, String otp, String purpose) {
        OtpRecord record = OtpRecord.builder()
                .phoneNumber(phoneNumber)
                .otpCode(otp)
                .purpose(purpose)
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(otpProperties.getExpiryMinutes()))
                .build();
        otpRecordRepository.save(record);
    }

    private void deliverOtp(String phoneNumber, String otp) {
        if (otpProperties.isMockEnabled()) {
            log.info("OTP for {} : {} (mock=true)", phoneNumber, otp);
        } else {
            notificationService.sendSMS(null, phoneNumber,
                    "Your Puduvandi verification code is " + otp + ". Valid for "
                            + otpProperties.getExpiryMinutes() + " minutes. Do not share this with anyone.");
        }
    }

    private void consumeOtp(String phoneNumber, String code, String purpose) {
        OtpRecord record = otpRecordRepository.findLatestValidOtpForPurpose(phoneNumber, purpose, LocalDateTime.now())
                .orElseThrow(() -> new UnauthorizedException("OTP has expired or is invalid."));
        if (!record.getOtpCode().equals(code)) {
            throw new UnauthorizedException("Incorrect OTP. Please try again.");
        }
        otpRecordRepository.markUsedByPhoneAndPurpose(phoneNumber, purpose);
    }

    private PhoneChangeRequestResponse toPhoneChangeResponse(PhoneChangeRequest r) {
        return new PhoneChangeRequestResponse(
                r.getId(),
                r.getUser().getId(),
                r.getUser().getFullName(),
                r.getOldPhoneNumber(),
                r.getNewPhoneNumber(),
                r.getStatus(),
                r.getRemarks(),
                r.getCreatedAt()
        );
    }

    private User findActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private UserProfileResponse toProfileResponse(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getRole(),
                user.getStatus(),
                user.getKycStatus(),
                user.getCreatedAt()
        );
    }

    private UserDocumentResponse toDocumentResponse(UserDocument doc) {
        return new UserDocumentResponse(
                doc.getId(),
                doc.getDocumentType(),
                doc.getDocumentUrl(),
                doc.getStatus(),
                doc.getRemarks(),
                doc.getCreatedAt()
        );
    }
}
