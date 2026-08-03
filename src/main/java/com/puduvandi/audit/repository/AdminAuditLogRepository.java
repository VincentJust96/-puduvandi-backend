package com.puduvandi.audit.repository;

import com.puduvandi.audit.entity.AdminAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {

    Page<AdminAuditLog> findByActorUserIdOrderByCreatedAtDesc(Long actorUserId, Pageable pageable);

    Page<AdminAuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    @Query("SELECT a FROM AdminAuditLog a WHERE " +
           "(:actorUserId IS NULL OR a.actorUserId = :actorUserId) AND " +
           "(:entityType IS NULL OR a.entityType = :entityType) " +
           "ORDER BY a.createdAt DESC")
    Page<AdminAuditLog> search(@Param("actorUserId") Long actorUserId,
                                @Param("entityType") String entityType,
                                Pageable pageable);
}
