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

    /**
     * Same as {@link #findLatestValidOtp} but scoped to a specific purpose
     * (e.g. "PHONE_ADD"/"PHONE_CHANGE") — a login OTP and a phone-add/change
     * OTP sent to the same number must never satisfy each other.
     */
    @Query("""
        SELECT o FROM OtpRecord o
        WHERE o.phoneNumber = :phone
          AND o.purpose = :purpose
          AND o.used = false
          AND o.expiresAt > :now
        ORDER BY o.createdAt DESC
        LIMIT 1
        """)
    Optional<OtpRecord> findLatestValidOtpForPurpose(String phone, String purpose, LocalDateTime now);

    @Modifying
    @Query("UPDATE OtpRecord o SET o.used = true WHERE o.phoneNumber = :phone AND o.purpose = :purpose")
    void markUsedByPhoneAndPurpose(String phone, String purpose);
}
