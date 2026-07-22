package com.puduvandi.admin.service;

import com.puduvandi.admin.dto.*;
import com.puduvandi.admin.entity.CommissionSettings;
import com.puduvandi.admin.repository.CommissionSettingsRepository;
import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.bike.dto.BikeResponse;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.bike.entity.BikeImage;
import com.puduvandi.bike.repository.BikeRepository;
import com.puduvandi.booking.dto.BookingResponse;
import com.puduvandi.booking.entity.Booking;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.bike.dto.UpdateBikeRequest;
import com.puduvandi.common.enums.*;
import com.puduvandi.delivery.dto.DeliveryRateResponse;
import com.puduvandi.delivery.dto.UpdateDeliveryRateRequest;
import com.puduvandi.delivery.entity.DeliverySettings;
import com.puduvandi.delivery.repository.DeliverySettingsRepository;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ConflictException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.owner.dto.CompleteOwnerProfileRequest;
import com.puduvandi.owner.dto.OwnerProfileResponse;
import com.puduvandi.owner.entity.OwnerDocument;
import com.puduvandi.owner.entity.OwnerProfile;
import com.puduvandi.owner.repository.OwnerDocumentRepository;
import com.puduvandi.owner.repository.OwnerProfileRepository;
import com.puduvandi.partner.dto.CompletePartnerProfileRequest;
import com.puduvandi.partner.dto.PartnerProfileResponse;
import com.puduvandi.partner.entity.PartnerDocument;
import com.puduvandi.partner.entity.PartnerProfile;
import com.puduvandi.partner.repository.PartnerDocumentRepository;
import com.puduvandi.partner.repository.PartnerProfileRepository;
import com.puduvandi.user.dto.PhoneChangeRequestResponse;
import com.puduvandi.user.entity.PhoneChangeRequest;
import com.puduvandi.user.entity.UserDocument;
import com.puduvandi.user.repository.PhoneChangeRequestRepository;
import com.puduvandi.user.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final PartnerProfileRepository partnerProfileRepository;
    private final BikeRepository bikeRepository;
    private final BookingRepository bookingRepository;
    private final CommissionSettingsRepository commissionSettingsRepository;
    private final DeliverySettingsRepository deliverySettingsRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final OwnerDocumentRepository ownerDocumentRepository;
    private final PhoneChangeRequestRepository phoneChangeRequestRepository;
    private final PartnerDocumentRepository partnerDocumentRepository;
    private final JdbcTemplate jdbcTemplate;

    /** PUDUVANDI_ENV values allowed to run the destructive local data reset. */
    private static final Set<String> RESET_ALLOWED_ENVS = Set.of("", "local");
    private static final String RESET_CONFIRMATION_PHRASE = "RESET_ALL_DATA";

    /**
     * Tables wiped by {@link #resetLocalData}, in an order that satisfies FK
     * constraints without relying solely on CASCADE. commission_settings and
     * delivery_settings are intentionally excluded — they're platform config,
     * not per-user test data. "users" is excluded here and handled separately
     * so the seeded ADMIN account survives the reset.
     */
    private static final String RESET_TRUNCATE_SQL = """
            TRUNCATE TABLE
                handover_otps,
                delivery_orders,
                notification_logs,
                bike_images,
                owner_documents,
                partner_documents,
                user_documents,
                refresh_tokens,
                otp_records,
                payments,
                bookings,
                bikes,
                owner_profiles,
                partner_profiles,
                stored_files,
                error_logs
            RESTART IDENTITY CASCADE
            """;

    // ===== DASHBOARD =====

    @Transactional(readOnly = true)
    public AdminDashboardStats getDashboardStats() {
        long totalCustomers = userRepository.countByRoleAndDeletedFalse(UserRole.CUSTOMER);
        long totalOwners    = userRepository.countByRoleAndDeletedFalse(UserRole.OWNER);
        long totalBikes     = bikeRepository.countByDeletedFalse();
        long pendingKyc     = ownerProfileRepository
                .findAllForAdmin(KycStatus.PENDING, PageRequest.of(0, 1)).getTotalElements();
        long pendingBikes   = bikeRepository
                .findAllForAdmin(BikeVerificationStatus.PENDING, PageRequest.of(0, 1)).getTotalElements();
        long pendingLicences = userDocumentRepository
                .findAllForAdmin(DocumentType.DRIVING_LICENSE, DocumentStatus.PENDING, PageRequest.of(0, 1))
                .getTotalElements();
        long activeBookings = bookingRepository.countByStatusAndDeletedFalse(BookingStatus.CONFIRMED)
                + bookingRepository.countByStatusAndDeletedFalse(BookingStatus.RIDE_STARTED);
        long completedBookings = bookingRepository.countByStatusAndDeletedFalse(BookingStatus.COMPLETED);
        double commission = getActiveCommission().commissionPercent().doubleValue();
        long totalPartners = userRepository.countByRoleAndDeletedFalse(UserRole.PARTNER);
        long pendingPartnerKyc = partnerProfileRepository
                .findAllForAdmin(KycStatus.PENDING, PageRequest.of(0, 1)).getTotalElements();

        return new AdminDashboardStats(
                totalCustomers, totalOwners, totalBikes,
                pendingKyc, pendingBikes, pendingLicences,
                activeBookings, completedBookings, commission,
                totalPartners, pendingPartnerKyc
        );
    }

    // ===== USERS =====

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(UserRole role, UserStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return userRepository.findAllForAdmin(role, status, pageable).map(this::toUserResponse);
    }

    @Transactional
    public AdminUserResponse suspendUser(Long userId) {
        User user = findUser(userId);
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException("Cannot suspend an admin account");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new BusinessException("User is already suspended");
        }
        user.setStatus(UserStatus.SUSPENDED);
        User saved = userRepository.save(user);
        log.info("Admin suspended userId={}", userId);
        return toUserResponse(saved);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = findUser(userId);
        if (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException("Cannot delete an admin account");
        }
        if (user.getRole() == UserRole.OWNER) {
            assertNoActiveOwnerBookings(userId);
            ownerProfileRepository.findByUserIdAndDeletedFalse(userId)
                    .ifPresent(this::softDeleteOwnerAndBikes);
        }
        // phone_number/email carry plain UNIQUE constraints (not partial on
        // is_deleted), so a soft-deleted row keeps permanently squatting on
        // its identifiers unless cleared here — otherwise no one could ever
        // sign up with that phone/email again, and it wouldn't even be
        // visible why (this account no longer shows up anywhere in admin).
        user.setDeleted(true);
        user.setPhoneNumber(null);
        user.setEmail(null);
        userRepository.save(user);
        log.info("Admin soft-deleted userId={} (phone/email cleared for reuse)", userId);
    }

    @Transactional
    public AdminUserResponse updateUser(Long userId, AdminUpdateUserRequest request) {
        User user = findUser(userId);
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        User saved = userRepository.save(user);
        log.info("Admin updated userId={}", userId);
        return toUserResponse(saved);
    }

    @Transactional
    public AdminUserResponse unsuspendUser(Long userId) {
        User user = findUser(userId);
        if (user.getStatus() != UserStatus.SUSPENDED) {
            throw new BusinessException("User is not currently suspended");
        }
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        log.info("Admin unsuspended userId={}", userId);
        return toUserResponse(saved);
    }

    // ===== OWNER KYC =====

    @Transactional(readOnly = true)
    public Page<AdminOwnerResponse> listOwners(KycStatus kycStatus, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ownerProfileRepository.findAllForAdmin(kycStatus, pageable).map(this::toOwnerResponse);
    }

    @Transactional
    public AdminOwnerResponse approveOwnerKyc(Long ownerId) {
        OwnerProfile owner = findOwnerProfile(ownerId);
        User user = owner.getUser();
        if (user.getKycStatus() != KycStatus.PENDING) {
            throw new BusinessException("KYC is not in PENDING state, current state: " + user.getKycStatus());
        }
        user.setKycStatus(KycStatus.APPROVED);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        cascadeOwnerDocumentStatus(owner, DocumentStatus.APPROVED);
        log.info("Admin approved KYC for ownerId={}", ownerId);
        return toOwnerResponse(owner);
    }

    @Transactional
    public AdminOwnerResponse rejectOwnerKyc(Long ownerId, String reason) {
        OwnerProfile owner = findOwnerProfile(ownerId);
        User user = owner.getUser();
        if (user.getKycStatus() != KycStatus.PENDING) {
            throw new BusinessException("KYC is not in PENDING state, current state: " + user.getKycStatus());
        }
        user.setKycStatus(KycStatus.REJECTED);
        userRepository.save(user);
        cascadeOwnerDocumentStatus(owner, DocumentStatus.REJECTED);
        log.info("Admin rejected KYC for ownerId={}, reason={}", ownerId, reason);
        return toOwnerResponse(owner);
    }

    // The admin KYC decision is holistic (one decision per owner, not one per
    // uploaded document — there's no separate per-document review screen for
    // owner KYC the way there is for driving licences), so approving/rejecting
    // the owner must carry that same status onto every document they submitted.
    // Otherwise a document stays stuck on PENDING forever even after the owner
    // shows as APPROVED, which is exactly what a user should never see.
    private void cascadeOwnerDocumentStatus(OwnerProfile owner, DocumentStatus status) {
        List<OwnerDocument> docs = ownerDocumentRepository.findByOwnerIdAndDeletedFalse(owner.getId());
        for (OwnerDocument doc : docs) {
            if (doc.getStatus() == DocumentStatus.PENDING) {
                doc.setStatus(status);
            }
        }
        ownerDocumentRepository.saveAll(docs);
    }

    @Transactional(readOnly = true)
    public OwnerProfileResponse getOwnerDetail(Long ownerId) {
        OwnerProfile owner = findOwnerProfile(ownerId);
        return toFullOwnerResponse(owner);
    }

    @Transactional
    public AdminOwnerResponse updateOwner(Long ownerId, CompleteOwnerProfileRequest request) {
        OwnerProfile owner = findOwnerProfile(ownerId);
        owner.setBusinessName(request.businessName());
        owner.setGstin(request.gstin());
        owner.setAddressLine1(request.addressLine1());
        owner.setAddressLine2(request.addressLine2());
        owner.setCity(request.city());
        owner.setState(request.state());
        owner.setPincode(request.pincode());
        OwnerProfile saved = ownerProfileRepository.save(owner);
        log.info("Admin updated ownerId={}", ownerId);
        return toOwnerResponse(saved);
    }

    @Transactional
    public void deleteOwner(Long ownerId) {
        OwnerProfile owner = findOwnerProfile(ownerId);
        assertNoActiveOwnerBookings(owner.getUser().getId());
        softDeleteOwnerAndBikes(owner);
        log.info("Admin soft-deleted ownerId={} (cascaded to bikes)", ownerId);
    }

    // ===== DELIVERY PARTNER KYC =====

    @Transactional(readOnly = true)
    public Page<AdminPartnerResponse> listPartners(KycStatus kycStatus, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return partnerProfileRepository.findAllForAdmin(kycStatus, pageable).map(this::toPartnerResponse);
    }

    @Transactional
    public AdminPartnerResponse approvePartnerKyc(Long partnerId) {
        PartnerProfile partner = findPartnerProfile(partnerId);
        User user = partner.getUser();
        if (user.getKycStatus() != KycStatus.PENDING) {
            throw new BusinessException("KYC is not in PENDING state, current state: " + user.getKycStatus());
        }
        user.setKycStatus(KycStatus.APPROVED);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        cascadePartnerDocumentStatus(partner, DocumentStatus.APPROVED);
        log.info("Admin approved KYC for partnerId={}", partnerId);
        return toPartnerResponse(partner);
    }

    @Transactional
    public AdminPartnerResponse rejectPartnerKyc(Long partnerId, String reason) {
        PartnerProfile partner = findPartnerProfile(partnerId);
        User user = partner.getUser();
        if (user.getKycStatus() != KycStatus.PENDING) {
            throw new BusinessException("KYC is not in PENDING state, current state: " + user.getKycStatus());
        }
        user.setKycStatus(KycStatus.REJECTED);
        userRepository.save(user);
        cascadePartnerDocumentStatus(partner, DocumentStatus.REJECTED);
        log.info("Admin rejected KYC for partnerId={}, reason={}", partnerId, reason);
        return toPartnerResponse(partner);
    }

    // Same holistic-decision reasoning as cascadeOwnerDocumentStatus — there's
    // no per-document review screen for partner KYC either, so the owner
    // decision must carry onto every document the partner submitted.
    private void cascadePartnerDocumentStatus(PartnerProfile partner, DocumentStatus status) {
        List<PartnerDocument> docs = partnerDocumentRepository.findByPartnerIdAndDeletedFalse(partner.getId());
        for (PartnerDocument doc : docs) {
            if (doc.getStatus() == DocumentStatus.PENDING) {
                doc.setStatus(status);
            }
        }
        partnerDocumentRepository.saveAll(docs);
    }

    @Transactional(readOnly = true)
    public PartnerProfileResponse getPartnerDetail(Long partnerId) {
        PartnerProfile partner = findPartnerProfile(partnerId);
        return toFullPartnerResponse(partner);
    }

    @Transactional
    public AdminPartnerResponse updatePartner(Long partnerId, CompletePartnerProfileRequest request) {
        PartnerProfile partner = findPartnerProfile(partnerId);
        partner.setVehicleType(request.vehicleType());
        partner.setVehicleNumber(request.vehicleNumber());
        partner.setCity(request.city());
        PartnerProfile saved = partnerProfileRepository.save(partner);
        log.info("Admin updated partnerId={}", partnerId);
        return toPartnerResponse(saved);
    }

    @Transactional
    public void deletePartner(Long partnerId) {
        PartnerProfile partner = findPartnerProfile(partnerId);
        partner.setDeleted(true);
        partnerProfileRepository.save(partner);
        log.info("Admin soft-deleted partnerId={}", partnerId);
    }

    // ===== BIKES =====

    @Transactional(readOnly = true)
    public Page<BikeResponse> listBikes(BikeVerificationStatus verificationStatus, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return bikeRepository.findAllForAdmin(verificationStatus, pageable).map(this::toBikeResponse);
    }

    @Transactional
    public BikeResponse approveBike(Long bikeId) {
        Bike bike = findBike(bikeId);
        if (bike.getVerificationStatus() != BikeVerificationStatus.PENDING) {
            throw new BusinessException("Bike is not in PENDING state, current state: " + bike.getVerificationStatus());
        }
        bike.setVerificationStatus(BikeVerificationStatus.APPROVED);
        bike.setStatus(BikeStatus.AVAILABLE);
        Bike saved = bikeRepository.save(bike);
        log.info("Admin approved bikeId={}", bikeId);
        return toBikeResponse(saved);
    }

    @Transactional
    public BikeResponse rejectBike(Long bikeId, String reason) {
        Bike bike = findBike(bikeId);
        if (bike.getVerificationStatus() != BikeVerificationStatus.PENDING) {
            throw new BusinessException("Bike is not in PENDING state, current state: " + bike.getVerificationStatus());
        }
        bike.setVerificationStatus(BikeVerificationStatus.REJECTED);
        Bike saved = bikeRepository.save(bike);
        log.info("Admin rejected bikeId={}, reason={}", bikeId, reason);
        return toBikeResponse(saved);
    }

    @Transactional
    public BikeResponse updateBike(Long bikeId, UpdateBikeRequest request) {
        Bike bike = findBike(bikeId);
        if (bookingRepository.existsActiveLockingBookingForBike(bikeId)) {
            throw new ConflictException(
                "This bike has an active booking and cannot be edited until it is completed or cancelled.");
        }
        bike.setBrand(request.brand());
        bike.setModel(request.model());
        bike.setYear(request.year());
        bike.setFuelType(request.fuelType());
        bike.setTransmission(request.transmission());
        bike.setEngineCapacity(request.engineCapacity());
        bike.setHelmetIncluded(request.helmetIncluded());
        bike.setPricePerHour(request.pricePerHour());
        bike.setPricePerDay(request.pricePerDay());
        bike.setSecurityDeposit(request.securityDeposit());
        bike.setDescription(request.description());
        if (request.latitude() != null && request.longitude() != null) {
            bike.setLatitude(request.latitude());
            bike.setLongitude(request.longitude());
        }
        bike.getImages().clear();
        addImages(bike, request.imageUrls());
        Bike saved = bikeRepository.save(bike);
        log.info("Admin updated bikeId={}", bikeId);
        return toBikeResponse(saved);
    }

    @Transactional
    public void deleteBike(Long bikeId) {
        Bike bike = findBike(bikeId);
        if (bike.getStatus() == BikeStatus.RESERVED) {
            throw new BusinessException("Cannot delete a bike that is currently reserved.");
        }
        bike.setDeleted(true);
        bikeRepository.save(bike);
        log.info("Admin soft-deleted bikeId={}", bikeId);
    }

    // ===== BOOKINGS =====

    @Transactional(readOnly = true)
    public Page<BookingResponse> listBookings(BookingStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (status != null) {
            return bookingRepository.findByStatusAndDeletedFalse(status, pageable).map(this::toBookingResponse);
        }
        return bookingRepository.findAllActiveBookings(pageable).map(this::toBookingResponse);
    }

    // ===== DOCUMENTS (driving licence review) =====

    @Transactional(readOnly = true)
    public Page<AdminUserDocumentResponse> listDocuments(
            DocumentType documentType, DocumentStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return userDocumentRepository.findAllForAdmin(documentType, status, pageable)
                .map(this::toDocumentResponse);
    }

    @Transactional
    public AdminUserDocumentResponse approveDocument(Long documentId) {
        UserDocument document = findDocument(documentId);
        if (document.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("Document is not in PENDING state, current state: " + document.getStatus());
        }
        document.setStatus(DocumentStatus.APPROVED);
        document.setRemarks(null);
        UserDocument saved = userDocumentRepository.save(document);
        log.info("Admin approved documentId={}", documentId);
        return toDocumentResponse(saved);
    }

    @Transactional
    public AdminUserDocumentResponse rejectDocument(Long documentId, String reason) {
        UserDocument document = findDocument(documentId);
        if (document.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("Document is not in PENDING state, current state: " + document.getStatus());
        }
        document.setStatus(DocumentStatus.REJECTED);
        document.setRemarks(reason);
        UserDocument saved = userDocumentRepository.save(document);
        log.info("Admin rejected documentId={}, reason={}", documentId, reason);
        return toDocumentResponse(saved);
    }

    private UserDocument findDocument(Long documentId) {
        return userDocumentRepository.findById(documentId)
                .filter(d -> !d.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("UserDocument", documentId));
    }

    private AdminUserDocumentResponse toDocumentResponse(UserDocument document) {
        User user = document.getUser();
        return new AdminUserDocumentResponse(
                document.getId(),
                user.getId(),
                user.getFullName(),
                user.getPhoneNumber(),
                document.getDocumentType(),
                document.getDocumentUrl(),
                document.getStatus(),
                document.getRemarks(),
                document.getCreatedAt()
        );
    }

    // ===== PHONE CHANGE REQUESTS =====

    @Transactional(readOnly = true)
    public Page<PhoneChangeRequestResponse> listPhoneChangeRequests(DocumentStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return phoneChangeRequestRepository.findAllForAdmin(status, pageable).map(this::toPhoneChangeResponse);
    }

    @Transactional
    public PhoneChangeRequestResponse approvePhoneChangeRequest(Long requestId) {
        PhoneChangeRequest request = findPhoneChangeRequest(requestId);
        if (request.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("Request is not in PENDING state, current state: " + request.getStatus());
        }
        // Re-check uniqueness — the number could have been claimed by someone
        // else in the time this request sat waiting for review.
        if (userRepository.existsByPhoneNumber(request.getNewPhoneNumber())) {
            throw new ConflictException("This phone number is already linked to another account.");
        }

        User user = request.getUser();
        user.setPhoneNumber(request.getNewPhoneNumber());
        userRepository.save(user);

        request.setStatus(DocumentStatus.APPROVED);
        PhoneChangeRequest saved = phoneChangeRequestRepository.save(request);
        log.info("Admin approved phone change requestId={}, userId={}", requestId, user.getId());
        return toPhoneChangeResponse(saved);
    }

    @Transactional
    public PhoneChangeRequestResponse rejectPhoneChangeRequest(Long requestId, String reason) {
        PhoneChangeRequest request = findPhoneChangeRequest(requestId);
        if (request.getStatus() != DocumentStatus.PENDING) {
            throw new BusinessException("Request is not in PENDING state, current state: " + request.getStatus());
        }
        request.setStatus(DocumentStatus.REJECTED);
        request.setRemarks(reason);
        PhoneChangeRequest saved = phoneChangeRequestRepository.save(request);
        log.info("Admin rejected phone change requestId={}, reason={}", requestId, reason);
        return toPhoneChangeResponse(saved);
    }

    private PhoneChangeRequest findPhoneChangeRequest(Long requestId) {
        return phoneChangeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("PhoneChangeRequest", requestId));
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

    // ===== COMMISSION =====

    @Transactional(readOnly = true)
    public CommissionResponse getActiveCommission() {
        CommissionSettings settings = commissionSettingsRepository
                .findTopByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new ResourceNotFoundException("CommissionSettings", 1L));
        return toCommissionResponse(settings);
    }

    @Transactional
    public CommissionResponse updateCommission(Long adminUserId, UpdateCommissionRequest request) {
        User admin = findUser(adminUserId);
        CommissionSettings settings = commissionSettingsRepository
                .findTopByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new ResourceNotFoundException("CommissionSettings", 1L));
        settings.setCommissionPercent(request.commissionPercent());
        settings.setUpdatedByAdmin(admin);
        CommissionSettings saved = commissionSettingsRepository.save(settings);
        log.info("Admin updated commission to {}% by adminId={}", request.commissionPercent(), adminUserId);
        return toCommissionResponse(saved);
    }

    // ===== Private Helpers =====

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private OwnerProfile findOwnerProfile(Long ownerId) {
        return ownerProfileRepository.findById(ownerId)
                .filter(o -> !o.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("OwnerProfile", ownerId));
    }

    private PartnerProfile findPartnerProfile(Long partnerId) {
        return partnerProfileRepository.findById(partnerId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("PartnerProfile", partnerId));
    }

    private AdminPartnerResponse toPartnerResponse(PartnerProfile partner) {
        User user = partner.getUser();
        return new AdminPartnerResponse(
                partner.getId(),
                user.getId(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getKycStatus(),
                partner.getVehicleType(),
                partner.getVehicleNumber(),
                partner.getCity(),
                partner.getTotalDeliveries(),
                partner.getCreatedAt()
        );
    }

    private PartnerProfileResponse toFullPartnerResponse(PartnerProfile partner) {
        User user = partner.getUser();
        return new PartnerProfileResponse(
                partner.getId(), user.getId(), user.getPhoneNumber(), user.getFullName(),
                user.getKycStatus(), partner.getVehicleType(), partner.getVehicleNumber(),
                partner.getCity(), partner.getTotalDeliveries(), partner.getCreatedAt());
    }

    private Bike findBike(Long bikeId) {
        return bikeRepository.findById(bikeId)
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Bike", bikeId));
    }

    private void assertNoActiveOwnerBookings(Long ownerUserId) {
        long activeBookings = bookingRepository.countByOwner_UserIdAndStatusInAndDeletedFalse(
                ownerUserId,
                List.of(BookingStatus.CONFIRMED, BookingStatus.RIDE_STARTED, BookingStatus.RETURN_REQUESTED));
        if (activeBookings > 0) {
            throw new BusinessException(
                "Cannot delete this owner: they have " + activeBookings + " active booking(s). "
                + "Resolve or wait for these to complete/cancel first.");
        }
    }

    private void softDeleteOwnerAndBikes(OwnerProfile owner) {
        owner.setDeleted(true);
        ownerProfileRepository.save(owner);
        List<Bike> bikes = bikeRepository.findAllByOwnerIdAndDeletedFalse(owner.getId());
        bikes.forEach(b -> b.setDeleted(true));
        bikeRepository.saveAll(bikes);
    }

    private void addImages(Bike bike, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        for (int i = 0; i < imageUrls.size(); i++) {
            bike.getImages().add(BikeImage.builder()
                    .bike(bike).imageUrl(imageUrls.get(i)).sortOrder(i).build());
        }
    }

    private OwnerProfileResponse toFullOwnerResponse(OwnerProfile owner) {
        User user = owner.getUser();
        return new OwnerProfileResponse(
                owner.getId(), user.getId(), user.getPhoneNumber(), user.getFullName(),
                user.getKycStatus(), owner.getBusinessName(), owner.getGstin(),
                owner.getAddressLine1(), owner.getAddressLine2(), owner.getCity(),
                owner.getState(), owner.getPincode(),
                owner.getTotalBikes(), owner.getCreatedAt());
    }

    private AdminUserResponse toUserResponse(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getKycStatus(),
                user.getCreatedAt()
        );
    }

    private AdminOwnerResponse toOwnerResponse(OwnerProfile owner) {
        User user = owner.getUser();
        return new AdminOwnerResponse(
                owner.getId(),
                user.getId(),
                user.getPhoneNumber(),
                user.getFullName(),
                user.getKycStatus(),
                owner.getBusinessName(),
                owner.getCity(),
                owner.getState(),
                owner.getTotalBikes(),
                owner.getCreatedAt()
        );
    }

    private BikeResponse toBikeResponse(Bike bike) {
        List<String> imageUrls = bike.getImages().stream()
                .map(BikeImage::getImageUrl)
                .toList();
        String ownerName = bike.getOwner() != null && bike.getOwner().getUser() != null
                ? bike.getOwner().getUser().getFullName() : null;
        return new BikeResponse(
                bike.getId(),
                bike.getOwner() != null ? bike.getOwner().getId() : null,
                ownerName,
                bike.getBrand(),
                bike.getModel(),
                bike.getYear(),
                bike.getRegistrationNumber(),
                bike.getFuelType(),
                bike.getTransmission(),
                bike.getEngineCapacity(),
                bike.isHelmetIncluded(),
                bike.getPricePerHour(),
                bike.getPricePerDay(),
                bike.getSecurityDeposit(),
                bike.getDescription(),
                bike.getStatus(),
                bike.getVerificationStatus(),
                imageUrls,
                bike.getCreatedAt(),
                bike.getLatitude(),
                bike.getLongitude(),
                bike.getArea()
        );
    }

    private BookingResponse toBookingResponse(Booking booking) {
        boolean dayMode = booking.getTotalDays().compareTo(java.math.BigDecimal.ONE) >= 0;
        int quantity = dayMode
                ? booking.getTotalDays().setScale(0, java.math.RoundingMode.CEILING).intValue()
                : booking.getTotalHours().setScale(0, java.math.RoundingMode.CEILING).intValue();
        return new BookingResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getCustomer().getId(),
                booking.getCustomer().getFullName(),
                booking.getBike().getId(),
                booking.getBike().getBrand(),
                booking.getBike().getModel(),
                booking.getBike().getRegistrationNumber(),
                booking.getPickupDatetime(),
                booking.getReturnDatetime(),
                booking.getActualReturnDatetime(),
                booking.getTotalHours(),
                booking.getTotalDays(),
                booking.getBaseAmount(),
                booking.getSecurityDeposit(),
                booking.getTotalAmount(),
                booking.getAmountPaid(),
                booking.getCommissionPercent(),
                booking.getCommissionAmount(),
                booking.getOwnerEarning(),
                booking.getStatus(),
                booking.isHelmetIncluded(),
                booking.getCancellationReason(),
                booking.getCreatedAt(),
                dayMode ? "DAY" : "HOUR",
                quantity,
                booking.getDeliveryType(),
                userDocumentRepository
                        .findByUserIdAndDocumentTypeAndDeletedFalse(booking.getCustomer().getId(), DocumentType.DRIVING_LICENSE)
                        .map(UserDocument::getDocumentUrl)
                        .orElse(null),
                booking.getDepositStatus(),
                booking.getDepositRefundAmount()
        );
    }

    private CommissionResponse toCommissionResponse(CommissionSettings settings) {
        String adminName = settings.getUpdatedByAdmin() != null
                ? settings.getUpdatedByAdmin().getFullName() : null;
        return new CommissionResponse(
                settings.getId(),
                settings.getCommissionPercent(),
                adminName,
                settings.getUpdatedAt()
        );
    }

    // ===== DELIVERY RATE =====

    @Transactional(readOnly = true)
    public DeliveryRateResponse getActiveDeliveryRate() {
        DeliverySettings settings = deliverySettingsRepository
                .findTopByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new ResourceNotFoundException("DeliverySettings", 1L));
        return toDeliveryRateResponse(settings);
    }

    @Transactional
    public DeliveryRateResponse updateDeliveryRate(Long adminUserId, UpdateDeliveryRateRequest request) {
        User admin = findUser(adminUserId);
        DeliverySettings settings = deliverySettingsRepository
                .findTopByActiveTrueOrderByIdDesc()
                .orElseGet(() -> DeliverySettings.builder().active(true).build());
        settings.setRatePerKm(request.ratePerKm());
        settings.setUpdatedByAdmin(admin);
        DeliverySettings saved = deliverySettingsRepository.save(settings);
        log.info("Admin updated delivery rate to ₹{}/km by adminId={}", request.ratePerKm(), adminUserId);
        return toDeliveryRateResponse(saved);
    }

    private DeliveryRateResponse toDeliveryRateResponse(DeliverySettings settings) {
        String adminName = settings.getUpdatedByAdmin() != null
                ? settings.getUpdatedByAdmin().getFullName() : null;
        return new DeliveryRateResponse(
                settings.getId(),
                settings.getRatePerKm(),
                adminName,
                settings.getUpdatedAt()
        );
    }

    // ===== LOCAL DATA RESET (DANGER — dev/test only) =====

    /**
     * Wipes every owner, customer, partner, bike, booking and related row,
     * leaving only ADMIN/SUPER_ADMIN users behind. Refuses to run unless PUDUVANDI_ENV is
     * unset or "local" — never staging/production — and requires the caller
     * to echo back {@value #RESET_CONFIRMATION_PHRASE} to guard against an
     * accidental click/replay.
     */
    @Transactional
    public AdminDataResetResponse resetLocalData(String confirmationPhrase) {
        String env = System.getenv("PUDUVANDI_ENV");
        String normalizedEnv = env == null ? "" : env.trim().toLowerCase();

        if (!RESET_ALLOWED_ENVS.contains(normalizedEnv)) {
            throw new ForbiddenException(
                    "Local data reset is disabled outside local environments (PUDUVANDI_ENV=" + env + ").");
        }
        if (!RESET_CONFIRMATION_PHRASE.equals(confirmationPhrase)) {
            throw new BusinessException(
                    "Confirmation phrase mismatch. Pass confirmationPhrase=\"" + RESET_CONFIRMATION_PHRASE + "\" to proceed.");
        }

        log.warn("ADMIN DATA RESET triggered — wiping all non-admin data (PUDUVANDI_ENV={})", env);

        jdbcTemplate.execute(RESET_TRUNCATE_SQL);
        int usersRemoved = jdbcTemplate.update("DELETE FROM users WHERE role NOT IN ('ADMIN', 'SUPER_ADMIN') OR role IS NULL");

        log.warn("ADMIN DATA RESET complete — {} non-admin user(s) removed", usersRemoved);

        return new AdminDataResetResponse(usersRemoved, env == null ? "local (unset)" : env);
    }
}
