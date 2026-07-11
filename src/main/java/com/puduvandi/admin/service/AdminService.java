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
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.owner.dto.CompleteOwnerProfileRequest;
import com.puduvandi.owner.dto.OwnerProfileResponse;
import com.puduvandi.owner.entity.OwnerProfile;
import com.puduvandi.owner.repository.OwnerProfileRepository;
import com.puduvandi.user.entity.UserDocument;
import com.puduvandi.user.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final BikeRepository bikeRepository;
    private final BookingRepository bookingRepository;
    private final CommissionSettingsRepository commissionSettingsRepository;
    private final DeliverySettingsRepository deliverySettingsRepository;
    private final UserDocumentRepository userDocumentRepository;

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

        return new AdminDashboardStats(
                totalCustomers, totalOwners, totalBikes,
                pendingKyc, pendingBikes, pendingLicences,
                activeBookings, completedBookings, commission
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
        if (user.getRole() == UserRole.ADMIN) {
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
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException("Cannot delete an admin account");
        }
        if (user.getRole() == UserRole.OWNER) {
            assertNoActiveOwnerBookings(userId);
            ownerProfileRepository.findByUserIdAndDeletedFalse(userId)
                    .ifPresent(this::softDeleteOwnerAndBikes);
        }
        user.setDeleted(true);
        userRepository.save(user);
        log.info("Admin soft-deleted userId={}", userId);
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
        log.info("Admin rejected KYC for ownerId={}, reason={}", ownerId, reason);
        return toOwnerResponse(owner);
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
        owner.setBankAccountNumber(request.bankAccountNumber());
        owner.setBankIfscCode(request.bankIfscCode());
        owner.setBankName(request.bankName());
        owner.setAccountHolderName(request.accountHolderName());
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
                owner.getState(), owner.getPincode(), owner.getBankAccountNumber(),
                owner.getBankIfscCode(), owner.getBankName(), owner.getAccountHolderName(),
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
                booking.getCommissionPercent(),
                booking.getCommissionAmount(),
                booking.getOwnerEarning(),
                booking.getStatus(),
                booking.isHelmetIncluded(),
                booking.getCancellationReason(),
                booking.getCreatedAt(),
                dayMode ? "DAY" : "HOUR",
                quantity,
                booking.getDeliveryType()
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
                .orElseThrow(() -> new ResourceNotFoundException("DeliverySettings", 1L));
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
}
