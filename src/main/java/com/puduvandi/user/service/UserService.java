package com.puduvandi.user.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.user.dto.UpdateProfileRequest;
import com.puduvandi.user.dto.UploadDocumentRequest;
import com.puduvandi.user.dto.UserDocumentResponse;
import com.puduvandi.user.dto.UserProfileResponse;
import com.puduvandi.user.entity.UserDocument;
import com.puduvandi.user.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = findActiveUser(userId);
        return toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveUser(userId);

        user.setFullName(request.fullName());
        user.setEmail(request.email());
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

    // ===== Private Helpers =====

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
