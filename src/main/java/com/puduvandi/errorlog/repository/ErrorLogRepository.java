package com.puduvandi.errorlog.repository;

import com.puduvandi.errorlog.entity.ErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    Page<ErrorLog> findBySeverityOrderByCreatedAtDesc(String severity, Pageable pageable);

    Page<ErrorLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Long entityId, Pageable pageable);

    Page<ErrorLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT e FROM ErrorLog e ORDER BY e.createdAt DESC")
    Page<ErrorLog> findAllOrderByCreatedAtDesc(Pageable pageable);
}
