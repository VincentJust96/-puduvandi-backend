package com.puduvandi.delivery.repository;

import com.puduvandi.common.enums.DeliveryLegType;
import com.puduvandi.common.enums.DeliveryStatus;
import com.puduvandi.delivery.entity.DeliveryOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    Optional<DeliveryOrder> findByBookingIdAndLegType(Long bookingId, DeliveryLegType legType);

    List<DeliveryOrder> findByStatusOrderByCreatedAtAsc(DeliveryStatus status);

    List<DeliveryOrder> findByPartnerIdOrderByCreatedAtDesc(Long partnerId);

    /**
     * Acquires a row-level pessimistic write lock on the delivery order, serializing
     * concurrent claim attempts by different partners for the same job so the PENDING
     * check in DeliveryService.claim() can't race (two partners both passing the check
     * before either save() commits).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DeliveryOrder d WHERE d.id = :id")
    Optional<DeliveryOrder> lockById(@Param("id") Long id);
}
