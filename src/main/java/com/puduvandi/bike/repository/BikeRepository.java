package com.puduvandi.bike.repository;

import com.puduvandi.bike.entity.Bike;
import com.puduvandi.common.enums.BikeStatus;
import com.puduvandi.common.enums.BikeVerificationStatus;
import com.puduvandi.common.enums.FuelType;
import com.puduvandi.common.enums.TransmissionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface BikeRepository extends JpaRepository<Bike, Long> {

    Optional<Bike> findByIdAndDeletedFalseAndVerificationStatus(
            Long id, BikeVerificationStatus verificationStatus);

    Page<Bike> findByOwnerIdAndDeletedFalse(Long ownerId, Pageable pageable);

    Optional<Bike> findByIdAndOwnerIdAndDeletedFalse(Long id, Long ownerId);

    boolean existsByRegistrationNumber(String registrationNumber);

    @Query("""
        SELECT b FROM Bike b
        WHERE b.deleted = false
          AND b.verificationStatus = com.puduvandi.common.enums.BikeVerificationStatus.APPROVED
          AND b.status = com.puduvandi.common.enums.BikeStatus.AVAILABLE
          AND (:brand IS NULL OR LOWER(b.brand) LIKE LOWER(CONCAT('%', cast(:brand as string), '%')))
          AND (:model IS NULL OR LOWER(b.model) LIKE LOWER(CONCAT('%', cast(:model as string), '%')))
          AND (:area IS NULL OR LOWER(b.area) LIKE LOWER(CONCAT('%', cast(:area as string), '%')))
          AND (:fuelType IS NULL OR b.fuelType = :fuelType)
          AND (:transmission IS NULL OR b.transmission = :transmission)
          AND (:minPrice IS NULL OR b.pricePerDay >= :minPrice)
          AND (:maxPrice IS NULL OR b.pricePerDay <= :maxPrice)
          AND (:helmetIncluded IS NULL OR b.helmetIncluded = :helmetIncluded)
          AND (:search IS NULL
               OR LOWER(b.brand) LIKE LOWER(CONCAT('%', cast(:search as string), '%'))
               OR LOWER(b.model) LIKE LOWER(CONCAT('%', cast(:search as string), '%'))
               OR LOWER(b.area) LIKE LOWER(CONCAT('%', cast(:search as string), '%')))
        """)
    Page<Bike> browseAvailableBikes(
            String brand,
            String model,
            String area,
            FuelType fuelType,
            TransmissionType transmission,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean helmetIncluded,
            String search,
            Pageable pageable
    );

    @Query("""
        SELECT b FROM Bike b
        WHERE b.deleted = false
          AND (:verificationStatus IS NULL OR b.verificationStatus = :verificationStatus)
        """)
    Page<Bike> findAllForAdmin(BikeVerificationStatus verificationStatus, Pageable pageable);

    long countByOwnerIdAndDeletedFalse(Long ownerId);

    List<Bike> findByOwnerIdAndStatusAndDeletedFalse(Long ownerId, BikeStatus status);

    List<Bike> findAllByOwnerIdAndDeletedFalse(Long ownerId);

    long countByDeletedFalse();

    /**
     * Acquires a row-level pessimistic write lock on the bike, serializing
     * concurrent booking attempts for the same bike so the overlap check in
     * BookingService.createSingleBooking() can't race (TOCTOU double-booking).
     * Must be called inside an existing @Transactional boundary and the lock
     * held through the booking save().
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bike b WHERE b.id = :id")
    Optional<Bike> lockById(@Param("id") Long id);
}
