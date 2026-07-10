package com.puduvandi.delivery.repository;

import com.puduvandi.common.enums.DeliveryStatus;
import com.puduvandi.delivery.entity.DeliveryOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    Optional<DeliveryOrder> findByBookingId(Long bookingId);

    List<DeliveryOrder> findByStatusOrderByCreatedAtAsc(DeliveryStatus status);

    List<DeliveryOrder> findByPartnerIdOrderByCreatedAtDesc(Long partnerId);
}
