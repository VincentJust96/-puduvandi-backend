package com.puduvandi.bike.service;

import com.puduvandi.bike.dto.AddBikeRequest;
import com.puduvandi.bike.dto.BikeResponse;
import com.puduvandi.bike.dto.UpdateBikeRequest;
import com.puduvandi.bike.entity.Bike;
import com.puduvandi.bike.entity.BikeImage;
import com.puduvandi.bike.repository.BikeRepository;
import com.puduvandi.common.enums.BikeStatus;
import com.puduvandi.common.enums.BikeVerificationStatus;
import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;
import com.puduvandi.booking.repository.BookingRepository;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ConflictException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.owner.entity.OwnerProfile;
import com.puduvandi.owner.repository.OwnerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
        bike.setPricePerHour(request.pricePerHour());
        bike.setPricePerDay(request.pricePerDay());
        bike.setSecurityDeposit(request.securityDeposit());
        bike.setDescription(request.description());
        if (request.latitude() != null && request.longitude() != null) {
            bike.setLatitude(request.latitude());
            bike.setLongitude(request.longitude());
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
        return bikeRepository.findByOwnerIdAndDeletedFalse(
                owner.getId(), PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse);
    }

    // ===== PUBLIC / CUSTOMER OPERATIONS =====

    /**
     * Customer browses available bikes with optional filters.
     */
    @Transactional(readOnly = true)
    public Page<BikeResponse> browseAvailableBikes(
            String brand, String model,
            FuelType fuelType, TransmissionType transmission,
            BigDecimal minPrice, BigDecimal maxPrice,
            Boolean helmetIncluded, int page, int size) {

        return bikeRepository.browseAvailableBikes(
                brand, model, fuelType, transmission, minPrice, maxPrice, helmetIncluded,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponse);
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
                bike.getPricePerHour(),
                bike.getPricePerDay(),
                bike.getSecurityDeposit(),
                bike.getDescription(),
                bike.getStatus(),
                bike.getVerificationStatus(),
                imageUrls,
                bike.getCreatedAt(),
                bike.getLatitude(),
                bike.getLongitude()
        );
    }
}
