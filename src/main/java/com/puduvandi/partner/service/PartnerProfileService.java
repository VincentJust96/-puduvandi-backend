package com.puduvandi.partner.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.delivery.repository.DeliveryOrderRepository;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.partner.dto.*;
import com.puduvandi.partner.entity.PartnerDocument;
import com.puduvandi.partner.entity.PartnerProfile;
import com.puduvandi.partner.repository.PartnerDocumentRepository;
import com.puduvandi.partner.repository.PartnerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Delivery partner profile + KYC, mirroring OwnerService's profile/document flow.
 * Approval gate is the shared User.kycStatus field — same mechanism the owner
 * KYC flow already uses, kept consistent for admin tooling reuse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerProfileService {

    private final UserRepository userRepository;
    private final PartnerProfileRepository partnerProfileRepository;
    private final PartnerDocumentRepository partnerDocumentRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;

    /**
     * NOT read-only: findOrCreatePartnerProfile() may insert an empty profile
     * on first call (auto-vivify) — a readOnly transaction would have Spring set
     * the JDBC connection read-only, and Postgres rejects that INSERT outright.
     */
    @Transactional
    public PartnerProfileResponse getProfile(Long userId) {
        User user = findUser(userId);
        PartnerProfile profile = findOrCreatePartnerProfile(user);
        return toResponse(user, profile);
    }

    @Transactional
    public PartnerProfileResponse completeProfile(Long userId, CompletePartnerProfileRequest request) {
        User user = findUser(userId);
        PartnerProfile profile = findOrCreatePartnerProfile(user);

        profile.setVehicleType(request.vehicleType());
        profile.setVehicleNumber(request.vehicleNumber());
        profile.setCity(request.city());

        PartnerProfile saved = partnerProfileRepository.save(profile);
        log.info("Partner profile completed for userId={}", userId);
        return toResponse(user, saved);
    }

    @Transactional
    public PartnerDocumentResponse uploadDocument(Long userId, UploadPartnerDocumentRequest request) {
        User user = findUser(userId);
        PartnerProfile profile = findOrCreatePartnerProfile(user);

        partnerDocumentRepository.findByPartnerIdAndDeletedFalse(profile.getId()).stream()
                .filter(d -> d.getDocumentType() == request.documentType())
                .findFirst()
                .ifPresent(existing -> {
                    existing.setDeleted(true);
                    partnerDocumentRepository.save(existing);
                });

        PartnerDocument doc = PartnerDocument.builder()
                .partner(profile)
                .documentType(request.documentType())
                .documentUrl(request.documentUrl())
                .status(DocumentStatus.PENDING)
                .deleted(false)
                .build();

        PartnerDocument saved = partnerDocumentRepository.save(doc);

        // Uploading a KYC document — not merely filling in vehicle/city
        // details — is what actually puts the partner up for admin review.
        if (user.getKycStatus() == KycStatus.NOT_SUBMITTED) {
            user.setKycStatus(KycStatus.PENDING);
            userRepository.save(user);
        }

        log.info("Partner document uploaded: type={}, userId={}", request.documentType(), userId);
        return toDocumentResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PartnerDocumentResponse> getMyDocuments(Long userId) {
        PartnerProfile profile = partnerProfileRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException("PartnerProfile", userId));
        return partnerDocumentRepository.findByPartnerIdAndDeletedFalse(profile.getId())
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PartnerDashboardResponse getDashboard(Long userId) {
        User user = findUser(userId);
        long totalDeliveries = deliveryOrderRepository.findByPartnerIdOrderByCreatedAtDesc(userId).size();
        return new PartnerDashboardResponse(user.getKycStatus(), totalDeliveries);
    }

    // ===== Private Helpers =====

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private PartnerProfile findOrCreatePartnerProfile(User user) {
        return partnerProfileRepository.findByUserIdAndDeletedFalse(user.getId())
                .orElseGet(() -> {
                    PartnerProfile profile = PartnerProfile.builder()
                            .user(user)
                            .totalDeliveries(0)
                            .deleted(false)
                            .build();
                    return partnerProfileRepository.save(profile);
                });
    }

    private PartnerProfileResponse toResponse(User user, PartnerProfile profile) {
        return new PartnerProfileResponse(
                profile.getId(),
                user.getId(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getKycStatus(),
                profile.getVehicleType(),
                profile.getVehicleNumber(),
                profile.getCity(),
                profile.getTotalDeliveries(),
                profile.getCreatedAt()
        );
    }

    private PartnerDocumentResponse toDocumentResponse(PartnerDocument doc) {
        return new PartnerDocumentResponse(
                doc.getId(),
                doc.getDocumentType(),
                doc.getDocumentUrl(),
                doc.getStatus(),
                doc.getRemarks(),
                doc.getCreatedAt()
        );
    }
}
