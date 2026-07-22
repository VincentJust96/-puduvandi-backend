package com.puduvandi.partner.repository;

import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.partner.entity.PartnerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerProfileRepository extends JpaRepository<PartnerProfile, Long> {

    Optional<PartnerProfile> findByUserIdAndDeletedFalse(Long userId);

    @Query("SELECT pp FROM PartnerProfile pp JOIN pp.user u WHERE pp.deleted = false AND u.kycStatus = :kycStatus")
    List<PartnerProfile> findAllByUserKycStatusAndDeletedFalse(KycStatus kycStatus);

    boolean existsByUserId(Long userId);

    @Query("""
        SELECT pp FROM PartnerProfile pp JOIN pp.user u
        WHERE pp.deleted = false
          AND (:kycStatus IS NULL OR u.kycStatus = :kycStatus)
        """)
    Page<PartnerProfile> findAllForAdmin(KycStatus kycStatus, Pageable pageable);

    long countByDeletedFalse();
}
