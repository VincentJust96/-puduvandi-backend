package com.puduvandi.owner.repository;

import com.puduvandi.common.enums.KycStatus;
import com.puduvandi.owner.entity.OwnerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OwnerProfileRepository extends JpaRepository<OwnerProfile, Long> {

    Optional<OwnerProfile> findByUserIdAndDeletedFalse(Long userId);

    Optional<OwnerProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Query("""
        SELECT op FROM OwnerProfile op JOIN op.user u
        WHERE op.deleted = false
          AND (:kycStatus IS NULL OR u.kycStatus = :kycStatus)
        """)
    Page<OwnerProfile> findAllForAdmin(KycStatus kycStatus, Pageable pageable);

    long countByDeletedFalse();
}
