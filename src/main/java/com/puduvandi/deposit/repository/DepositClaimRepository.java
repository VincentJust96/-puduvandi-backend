package com.puduvandi.deposit.repository;

import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.deposit.entity.DepositClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DepositClaimRepository extends JpaRepository<DepositClaim, Long> {

    @Query("""
        SELECT c FROM DepositClaim c
        WHERE (:status IS NULL OR c.status = :status)
        ORDER BY c.createdAt DESC
        """)
    Page<DepositClaim> findAllForAdmin(DocumentStatus status, Pageable pageable);
}
