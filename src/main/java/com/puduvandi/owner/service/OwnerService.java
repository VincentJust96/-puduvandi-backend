package com.puduvandi.owner.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.bike.repository.BikeRepository;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.owner.dto.*;
import com.puduvandi.owner.entity.OwnerDocument;
import com.puduvandi.owner.entity.OwnerProfile;
import com.puduvandi.owner.repository.OwnerDocumentRepository;
import com.puduvandi.owner.repository.OwnerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerService {

    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final OwnerDocumentRepository ownerDocumentRepository;
    private final BikeRepository bikeRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public OwnerProfileResponse getProfile(Long userId) {
        User user = findUser(userId);
        OwnerProfile profile = findOrCreateOwnerProfile(user);
        return toResponse(user, profile);
    }

    @Transactional
    public OwnerProfileResponse completeProfile(Long userId, CompleteOwnerProfileRequest request) {
        User user = findUser(userId);
        OwnerProfile profile = findOrCreateOwnerProfile(user);

        profile.setBusinessName(request.businessName());
        profile.setGstin(request.gstin());
        profile.setAddressLine1(request.addressLine1());
        profile.setAddressLine2(request.addressLine2());
        profile.setCity(request.city());
        profile.setState(request.state());
        profile.setPincode(request.pincode());
        profile.setBankAccountNumber(request.bankAccountNumber());
        profile.setBankIfscCode(request.bankIfscCode());
        profile.setBankName(request.bankName());
        profile.setAccountHolderName(request.accountHolderName());

        if (user.getKycStatus() == KycStatus.NOT_SUBMITTED) {
            user.setKycStatus(KycStatus.PENDING);
            userRepository.save(user);
        }

        OwnerProfile saved = ownerProfileRepository.save(profile);
        log.info("Owner profile completed for userId={}", userId);
        return toResponse(user, saved);
    }

    @Transactional
    public OwnerDocumentResponse uploadDocument(Long userId, UploadOwnerDocumentRequest request) {
        User user = findUser(userId);
        OwnerProfile profile = findOrCreateOwnerProfile(user);

        ownerDocumentRepository.findByOwnerIdAndDeletedFalse(profile.getId()).stream()
                .filter(d -> d.getDocumentType() == request.documentType())
                .findFirst()
                .ifPresent(existing -> {
                    existing.setDeleted(true);
                    ownerDocumentRepository.save(existing);
                });

        OwnerDocument doc = OwnerDocument.builder()
                .owner(profile)
                .documentType(request.documentType())
                .documentUrl(request.documentUrl())
                .status(DocumentStatus.PENDING)
                .deleted(false)
                .build();

        OwnerDocument saved = ownerDocumentRepository.save(doc);
        log.info("Owner document uploaded: type={}, userId={}", request.documentType(), userId);
        return toDocumentResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OwnerDocumentResponse> getMyDocuments(Long userId) {
        OwnerProfile profile = ownerProfileRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException("OwnerProfile", userId));
        return ownerDocumentRepository.findByOwnerIdAndDeletedFalse(profile.getId())
                .stream()
                .map(this::toDocumentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OwnerDashboardResponse getOwnerDashboard(Long userId) {
        findUser(userId);

        // Do not auto-create the profile here — this is a read-only operation
        long totalBikes = ownerProfileRepository.findByUserIdAndDeletedFalse(userId)
                .map(p -> bikeRepository.countByOwnerIdAndDeletedFalse(p.getId()))
                .orElse(0L);

        long totalBookings = bookingRepository.countByOwner_UserIdAndDeletedFalse(userId);
        long activeBookings = bookingRepository.countByOwner_UserIdAndStatusInAndDeletedFalse(
                userId, List.of(BookingStatus.CONFIRMED, BookingStatus.RIDE_STARTED, BookingStatus.RETURN_REQUESTED));
        BigDecimal totalEarnings = bookingRepository.sumOwnerEarningsByUserIdAndStatus(userId, BookingStatus.COMPLETED);
        if (totalEarnings == null) totalEarnings = BigDecimal.ZERO;

        return new OwnerDashboardResponse(totalBikes, totalBookings, activeBookings, totalEarnings);
    }

    // ===== Private Helpers =====

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private OwnerProfile findOrCreateOwnerProfile(User user) {
        return ownerProfileRepository.findByUserIdAndDeletedFalse(user.getId())
                .orElseGet(() -> {
                    OwnerProfile profile = OwnerProfile.builder()
                            .user(user)
                            .totalBikes(0)
                            .deleted(false)
                            .build();
                    return ownerProfileRepository.save(profile);
                });
    }

    private OwnerProfileResponse toResponse(User user, OwnerProfile profile) {
        return new OwnerProfileResponse(
                profile.getId(),
                user.getId(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getKycStatus(),
                profile.getBusinessName(),
                profile.getGstin(),
                profile.getAddressLine1(),
                profile.getAddressLine2(),
                profile.getCity(),
                profile.getState(),
                profile.getPincode(),
                profile.getBankAccountNumber(),
                profile.getBankIfscCode(),
                profile.getBankName(),
                profile.getAccountHolderName(),
                profile.getTotalBikes(),
                profile.getCreatedAt()
        );
    }

    private OwnerDocumentResponse toDocumentResponse(OwnerDocument doc) {
        return new OwnerDocumentResponse(
                doc.getId(),
                doc.getDocumentType(),
                doc.getDocumentUrl(),
                doc.getStatus(),
                doc.getRemarks(),
                doc.getCreatedAt()
        );
    }
}
