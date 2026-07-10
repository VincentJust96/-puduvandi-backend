package com.puduvandi.booking.repository;

import com.puduvandi.booking.entity.Booking;
import com.puduvandi.common.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdAndDeletedFalse(Long id);

    Optional<Booking> findByBookingReferenceAndDeletedFalse(String bookingReference);

    /** Customer's booking history */
    Page<Booking> findByCustomerIdAndDeletedFalse(Long customerId, Pageable pageable);

    /** Owner's booking history across all their bikes (by OwnerProfile.id — admin use) */
    Page<Booking> findByOwnerIdAndDeletedFalse(Long ownerId, Pageable pageable);

    /** Owner's booking history by User.id (preferred — avoids OwnerProfile ID lookup) */
    Page<Booking> findByOwner_UserIdAndDeletedFalse(Long userId, Pageable pageable);

    long countByOwner_UserIdAndDeletedFalse(Long userId);

    long countByOwner_UserIdAndStatusInAndDeletedFalse(Long userId, List<BookingStatus> statuses);

    @Query("SELECT COALESCE(SUM(b.ownerEarning), 0) FROM Booking b " +
           "WHERE b.owner.user.id = :userId AND b.status = :status AND b.deleted = false")
    BigDecimal sumOwnerEarningsByUserIdAndStatus(@Param("userId") Long userId,
                                                 @Param("status") BookingStatus status);

    /** Customer's active booking for a specific bike */
    Optional<Booking> findByCustomerIdAndBikeIdAndStatusNotIn(
            Long customerId, Long bikeId, List<BookingStatus> excludedStatuses);

    /**
     * Checks if a bike has any overlapping active booking for a given time range.
     * Used to prevent double-booking.
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.bike.id = :bikeId
          AND b.deleted = false
          AND b.status NOT IN ('CANCELLED', 'COMPLETED')
          AND b.pickupDatetime < :returnDatetime
          AND b.returnDatetime > :pickupDatetime
        """)
    boolean existsOverlappingBooking(
            Long bikeId,
            LocalDateTime pickupDatetime,
            LocalDateTime returnDatetime
    );

    /**
     * Returns true if the bike has any booking that would lock owner edits
     * (CONFIRMED, RIDE_STARTED, or RETURN_REQUESTED).
     */
    @Query("""
        SELECT COUNT(b) > 0 FROM Booking b
        WHERE b.bike.id = :bikeId
          AND b.deleted = false
          AND b.status IN ('CONFIRMED', 'RIDE_STARTED', 'RETURN_REQUESTED')
        """)
    boolean existsActiveLockingBookingForBike(@Param("bikeId") Long bikeId);

    /** Count of bookings by status (for dashboard) */
    long countByStatusAndDeletedFalse(BookingStatus status);

    /** Owner: bookings for a specific bike */
    Page<Booking> findByBikeIdAndDeletedFalse(Long bikeId, Pageable pageable);

    /** Admin: bookings filtered by status */
    Page<Booking> findByStatusAndDeletedFalse(BookingStatus status, Pageable pageable);

    /** Admin: all bookings (no status filter) */
    @Query("SELECT b FROM Booking b WHERE b.deleted = false")
    Page<Booking> findAllActiveBookings(Pageable pageable);

    /** Next number for booking reference generation — survives restarts, unique across instances */
    @Query(value = "SELECT nextval('booking_reference_seq')", nativeQuery = true)
    long nextBookingReferenceNumber();
}
