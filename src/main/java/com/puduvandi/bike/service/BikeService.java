package com.puduvandi.bike.service;

import com.puduvandi.bike.dto.AddBikeRequest;
import com.puduvandi.bike.dto.BikeResponse;
import com.puduvandi.bike.dto.UpdateBikeRequest;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.bike.entity.BikeImage;
import com.puduvandi.bike.repository.BikeRepository;
import com.puduvandi.common.enums.BikeStatus;
import com.puduvandi.common.enums.BikeVerificationStatus;
import com.puduvandi.common.enums.BookingStatus;
import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ConflictException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.owner.entity.OwnerProfile;
import com.puduvandi.owner.repository.OwnerProfileRepository;
import com.puduvandi.review.repository.ReviewRepository;
import com.puduvandi.storage.service.FileStorageService;
import com.puduvandi.storage.service.InsuranceDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages bike listings: add, edit, delete, availability toggle, browse.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BikeService {

    private final BikeRepository bikeRepository;
    private final OwnerProfileRepository ownerProfileRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final FileStorageService fileStorageService;
    private final InsuranceDocumentParser insuranceDocumentParser;

    private static final Pattern FILE_ID_PATTERN = Pattern.compile("/files/(\\d+)");

    // ===== OWNER OPERATIONS =====

    /**
     * Owner adds a new bike listing.
     * Bike starts as UNAVAILABLE + PENDING verification.
     * Admin must approve before it shows to customers.
     */
    @Transactional
    public BikeResponse addBike(Long userId, AddBikeRequest request) {
        OwnerProfile owner = findOwnerProfile(userId);

        if (bikeRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw new BusinessException(
                    "A bike with registration number " + request.registrationNumber() + " already exists.");
        }

        Bike bike = Bike.builder()
                .owner(owner)
                .brand(request.brand())
                .model(request.model())
                .year(request.year())
                .registrationNumber(request.registrationNumber().toUpperCase())
                .fuelType(request.fuelType())
                .transmission(request.transmission())
                .engineCapacity(request.engineCapacity())
                .helmetIncluded(request.helmetIncluded())
                .papersIncluded(request.papersIncluded() == null ? Boolean.TRUE : request.papersIncluded())
                .fuelIncluded(request.fuelIncluded() == null ? Boolean.TRUE : request.fuelIncluded())
                .roadsideAssistance(request.roadsideAssistance() == null ? Boolean.TRUE : request.roadsideAssistance())
                .pricePerHour(request.pricePerHour())
                .pricePerDay(request.pricePerDay())
                .securityDeposit(request.securityDeposit())
                .description(request.description())
                .status(BikeStatus.UNAVAILABLE)
                .verificationStatus(BikeVerificationStatus.PENDING)
                .images(new ArrayList<>())
                .deleted(false)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .area(request.area())
                .rcDocumentUrl(request.rcDocumentUrl())
                .insuranceDocumentUrl(request.insuranceDocumentUrl())
                .insurancePolicyNumber(request.insurancePolicyNumber())
                .insuranceExpiryDate(request.insuranceExpiryDate())
                .insuranceDocumentPassword(request.insuranceDocumentPassword())
                .build();

        addImagesToNewBike(bike, request.imageUrls());
        Bike saved = bikeRepository.save(bike);

        log.info("Bike added: id={}, owner={}, reg={}", saved.getId(), userId, saved.getRegistrationNumber());
        return toResponse(saved);
    }

    /**
     * Owner updates bike details.
     * Only the bike's own owner can update it.
     */
    @Transactional
    public BikeResponse updateBike(Long userId, Long bikeId, UpdateBikeRequest request) {
        OwnerProfile owner = findOwnerProfile(userId);
        Bike bike = findBikeOwnedBy(bikeId, owner.getId());

        if (bookingRepository.existsActiveLockingBookingForBike(bikeId)) {
            throw new ConflictException(
                "This bike has an active booking and cannot be edited until the booking is completed or cancelled.");
        }

        bike.setBrand(request.brand());
        bike.setModel(request.model());
        bike.setYear(request.year());
        bike.setFuelType(request.fuelType());
        bike.setTransmission(request.transmission());
        bike.setEngineCapacity(request.engineCapacity());
        bike.setHelmetIncluded(request.helmetIncluded());
        // Null-guarded like latitude/area below: a client that omits these
        // fields must not silently flip an existing true flag to false.
        if (request.papersIncluded() != null) {
            bike.setPapersIncluded(request.papersIncluded());
        }
        if (request.fuelIncluded() != null) {
            bike.setFuelIncluded(request.fuelIncluded());
        }
        if (request.roadsideAssistance() != null) {
            bike.setRoadsideAssistance(request.roadsideAssistance());
        }
        bike.setPricePerHour(request.pricePerHour());
        bike.setPricePerDay(request.pricePerDay());
        bike.setSecurityDeposit(request.securityDeposit());
        bike.setDescription(request.description());
        if (request.latitude() != null && request.longitude() != null) {
            bike.setLatitude(request.latitude());
            bike.setLongitude(request.longitude());
        }
        if (request.area() != null) {
            bike.setArea(request.area());
        }
        bike.setRcDocumentUrl(request.rcDocumentUrl());
        bike.setInsuranceDocumentUrl(request.insuranceDocumentUrl());
        bike.setInsurancePolicyNumber(request.insurancePolicyNumber());
        bike.setInsuranceExpiryDate(request.insuranceExpiryDate());
        // Null-guarded unlike the fields above: toResponse() never echoes the real
        // password back to the owner (admin-only, see BikeResponse), so the owner's
        // edit form can't round-trip it — omitting it from the request must mean
        // "leave unchanged," not "clear it."
        if (request.insuranceDocumentPassword() != null) {
            bike.setInsuranceDocumentPassword(request.insuranceDocumentPassword());
        }

        // Replace images
        bike.getImages().clear();
        addImagesToNewBike(bike, request.imageUrls());

        Bike saved = bikeRepository.save(bike);
        log.info("Bike updated: id={}, owner={}", bikeId, userId);
        return toResponse(saved);
    }

    /**
     * Soft deletes a bike. Cannot delete a RESERVED bike.
     */
    @Transactional
    public void deleteBike(Long userId, Long bikeId) {
        OwnerProfile owner = findOwnerProfile(userId);
        Bike bike = findBikeOwnedBy(bikeId, owner.getId());

        if (bike.getStatus() == BikeStatus.RESERVED) {
            throw new BusinessException("Cannot delete a bike that is currently reserved.");
        }

        bike.setDeleted(true);
        bikeRepository.save(bike);
        log.info("Bike soft-deleted: id={}, owner={}", bikeId, userId);
    }

    /**
     * Owner toggles bike availability (AVAILABLE ↔ UNAVAILABLE).
     * Only APPROVED bikes can be made AVAILABLE.
     */
    @Transactional
    public BikeResponse toggleAvailability(Long userId, Long bikeId) {
        OwnerProfile owner = findOwnerProfile(userId);
        Bike bike = findBikeOwnedBy(bikeId, owner.getId());

        if (bike.getVerificationStatus() != BikeVerificationStatus.APPROVED) {
            throw new BusinessException("Only Admin-approved bikes can be made available.");
        }
        if (bike.getStatus() == BikeStatus.RESERVED) {
            throw new BusinessException("Cannot change availability of a currently reserved bike.");
        }

        BikeStatus newStatus = (bike.getStatus() == BikeStatus.AVAILABLE)
                ? BikeStatus.UNAVAILABLE
                : BikeStatus.AVAILABLE;

        bike.setStatus(newStatus);
        Bike saved = bikeRepository.save(bike);
        log.info("Bike availability toggled: id={}, newStatus={}", bikeId, newStatus);
        return toResponse(saved);
    }

    /**
     * Returns all bikes for the authenticated owner (paginated).
     */
    @Transactional(readOnly = true)
    public Page<BikeResponse> getMyBikes(Long userId, int page, int size) {
        OwnerProfile owner = findOwnerProfile(userId);
        Page<Bike> bikes = bikeRepository.findByOwnerIdAndDeletedFalse(
                owner.getId(), PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return toResponsePage(bikes);
    }

    // ===== PUBLIC / CUSTOMER OPERATIONS =====

    /**
     * Customer browses available bikes with optional filters.
     */
    @Transactional(readOnly = true)
    public Page<BikeResponse> browseAvailableBikes(
            String brand, String model, String area,
            FuelType fuelType, TransmissionType transmission,
            BigDecimal minPrice, BigDecimal maxPrice,
            Boolean helmetIncluded, String search, int page, int size) {

        Page<Bike> bikes = bikeRepository.browseAvailableBikes(
                brand, model, area, fuelType, transmission, minPrice, maxPrice, helmetIncluded, search,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return toResponsePage(bikes);
    }

    /**
     * Customer views full details of a single bike.
     */
    @Transactional(readOnly = true)
    public BikeResponse getBikeDetails(Long bikeId) {
        Bike bike = bikeRepository
                .findByIdAndDeletedFalseAndVerificationStatus(bikeId, BikeVerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Bike", bikeId));
        return toResponse(bike);
    }

    /**
     * Streams the bike's insurance PDF for inline viewing (e.g. in an &lt;iframe&gt;),
     * decrypting it first if the owner's stored password unlocks it. The password itself
     * never reaches the caller — it's applied here and the plain, unencrypted bytes are
     * what gets returned. Falls back to the original (still-encrypted) bytes if there's
     * no stored password or it doesn't work, so the browser's own PDF viewer can still
     * prompt for one as a last resort.
     */
    @Transactional(readOnly = true)
    public byte[] loadInsuranceDocument(Long bikeId) {
        Bike bike = bikeRepository
                .findByIdAndDeletedFalseAndVerificationStatus(bikeId, BikeVerificationStatus.APPROVED)
                .orElseThrow(() -> new ResourceNotFoundException("Bike", bikeId));

        String url = bike.getInsuranceDocumentUrl();
        if (url == null) {
            throw new ResourceNotFoundException("Insurance document", bikeId);
        }

        Matcher matcher = FILE_ID_PATTERN.matcher(url);
        if (!matcher.find()) {
            throw new BusinessException("Could not resolve the insurance document.");
        }
        Long fileId = Long.valueOf(matcher.group(1));

        byte[] rawBytes;
        try (InputStream in = fileStorageService.loadAsResource(fileId).getInputStream()) {
            rawBytes = in.readAllBytes();
        } catch (IOException ex) {
            throw new BusinessException("Could not read the insurance document.");
        }

        return bike.getInsuranceDocumentPassword() != null
                ? insuranceDocumentParser.decrypt(rawBytes, bike.getInsuranceDocumentPassword())
                : rawBytes;
    }

    // ===== PRIVATE HELPERS =====

    private OwnerProfile findOwnerProfile(Long userId) {
        return ownerProfileRepository.findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(
                        "Owner profile not found. Please complete your profile first."));
    }

    private Bike findBikeOwnedBy(Long bikeId, Long ownerProfileId) {
        return bikeRepository.findByIdAndOwnerIdAndDeletedFalse(bikeId, ownerProfileId)
                .orElseThrow(() -> new ForbiddenException(
                        "Bike not found or you do not have permission to modify it."));
    }

    private void addImagesToNewBike(Bike bike, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        for (int i = 0; i < imageUrls.size(); i++) {
            BikeImage image = BikeImage.builder()
                    .bike(bike)
                    .imageUrl(imageUrls.get(i))
                    .sortOrder(i)
                    .build();
            bike.getImages().add(image);
        }
    }

    public BikeResponse toResponse(Bike bike) {
        long totalTrips = bookingRepository.countByBikeIdAndStatusAndDeletedFalse(
                bike.getId(), BookingStatus.COMPLETED);
        Double rating = reviewRepository.averageRatingForBike(bike.getId());
        return toResponse(bike, totalTrips, rating);
    }

    /**
     * Batches the per-bike trip-count/rating lookups for an entire page into two queries
     * total instead of one-per-bike (was a real N+1: up to 2 extra queries per row on every
     * browse/my-bikes page — noticeable well before other parts of the system under load).
     */
    private Page<BikeResponse> toResponsePage(Page<Bike> bikes) {
        List<Long> bikeIds = bikes.getContent().stream().map(Bike::getId).toList();
        if (bikeIds.isEmpty()) {
            return bikes.map(bike -> toResponse(bike, 0L, null));
        }

        Map<Long, Long> tripCounts = bookingRepository
                .countCompletedTripsForBikes(bikeIds, BookingStatus.COMPLETED).stream()
                .collect(Collectors.toMap(BookingRepository.BikeTripCount::getBikeId,
                        BookingRepository.BikeTripCount::getTripCount));
        Map<Long, Double> ratings = reviewRepository.averageRatingsForBikes(bikeIds).stream()
                .collect(Collectors.toMap(ReviewRepository.BikeAverageRating::getBikeId,
                        ReviewRepository.BikeAverageRating::getAvgRating));

        return bikes.map(bike -> toResponse(bike,
                tripCounts.getOrDefault(bike.getId(), 0L),
                ratings.get(bike.getId())));
    }

    private BikeResponse toResponse(Bike bike, long totalTrips, Double rating) {
        List<String> imageUrls = bike.getImages().stream()
                .map(BikeImage::getImageUrl)
                .toList();

        String ownerName = (bike.getOwner() != null && bike.getOwner().getUser() != null)
                ? bike.getOwner().getUser().getFullName()
                : null;

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
                bike.isPapersIncluded(),
                bike.isFuelIncluded(),
                bike.isRoadsideAssistance(),
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
                bike.getArea(),
                totalTrips,
                rating,
                bike.getRcDocumentUrl(),
                bike.getInsuranceDocumentUrl(),
                bike.getInsurancePolicyNumber(),
                bike.getInsuranceExpiryDate(),
                // Admin/super-admin-only field — see BikeResponse's javadoc. Customer
                // browse/detail and owner add/update/my-bikes all go through this
                // mapper, so it must never leak the real password.
                null
        );
    }
}
