package com.puduvandi.notification.repository;

import com.puduvandi.common.enums.NotificationStatus;
import com.puduvandi.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByBookingId(Long bookingId);

    List<NotificationLog> findByStatusAndRetryCountLessThan(NotificationStatus status, int retryCount);
}
