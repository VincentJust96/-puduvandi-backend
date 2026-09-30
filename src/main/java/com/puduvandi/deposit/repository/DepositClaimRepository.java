package com.puduvandi.deposit.repository;

import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.deposit.entity.DepositClaim;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DepositClaimRepository extends JpaRepository<DepositClaim, Long> {

    @Query("""
        SELECT c FROM DepositClaim c
        WHERE (:status IS NULL OR c.status = :status)
        ORDER BY c.createdAt DESC
        """)
    Page<DepositClaim> findAllForAdmin(DocumentStatus status, Pageable pageable);

    /** Locks the claim row so two concurrent approve/reject calls can't both pass the PENDING check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM DepositClaim c WHERE c.id = :id")
    Optional<DepositClaim> lockById(@Param("id") Long id);
}
