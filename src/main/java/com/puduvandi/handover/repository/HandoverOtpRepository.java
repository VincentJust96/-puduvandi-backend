package com.puduvandi.handover.repository;

import com.puduvandi.common.enums.HandoverPurpose;
import com.puduvandi.handover.entity.HandoverOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HandoverOtpRepository extends JpaRepository<HandoverOtp, Long> {

    /** All not-yet-used rows for a booking+purpose (to invalidate on regeneration). */
    List<HandoverOtp> findByBookingIdAndPurposeAndUsedFalse(Long bookingId, HandoverPurpose purpose);

    /** The latest unused, unexpired OTP for a booking+purpose — the one currently valid for verification. */
    @Query("""
        SELECT h FROM HandoverOtp h
        WHERE h.bookingId = :bookingId
          AND h.purpose = :purpose
          AND h.used = false
          AND h.expiresAt > :now
        ORDER BY h.createdAt DESC
        LIMIT 1
        """)
    Optional<HandoverOtp> findLatestActive(@Param("bookingId") Long bookingId,
                                            @Param("purpose") HandoverPurpose purpose,
                                            @Param("now") LocalDateTime now);
}
