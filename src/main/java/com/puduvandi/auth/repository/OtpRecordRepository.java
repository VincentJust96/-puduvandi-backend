package com.puduvandi.auth.repository;

import com.puduvandi.auth.entity.OtpRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OtpRecordRepository extends JpaRepository<OtpRecord, Long> {

    /**
     * Finds the latest unused, unexpired OTP for the given phone number.
     */
    @Query("""
        SELECT o FROM OtpRecord o
        WHERE o.phoneNumber = :phone
          AND o.used = false
          AND o.expiresAt > :now
        ORDER BY o.createdAt DESC
        LIMIT 1
        """)
    Optional<OtpRecord> findLatestValidOtp(String phone, LocalDateTime now);

    /**
     * Marks all OTPs for a phone as used (cleanup after successful login).
     */
    @Modifying
    @Query("UPDATE OtpRecord o SET o.used = true WHERE o.phoneNumber = :phone")
    void markAllUsedByPhone(String phone);
}
