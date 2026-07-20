package com.puduvandi.user.repository;

import com.puduvandi.common.enums.DocumentStatus;
import com.puduvandi.user.entity.PhoneChangeRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PhoneChangeRequestRepository extends JpaRepository<PhoneChangeRequest, Long> {

    Optional<PhoneChangeRequest> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, DocumentStatus status);

    @Query("""
        SELECT r FROM PhoneChangeRequest r
        WHERE (:status IS NULL OR r.status = :status)
        ORDER BY r.createdAt DESC
        """)
    Page<PhoneChangeRequest> findAllForAdmin(DocumentStatus status, Pageable pageable);
}
