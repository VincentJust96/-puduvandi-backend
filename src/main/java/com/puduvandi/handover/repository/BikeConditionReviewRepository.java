package com.puduvandi.handover.repository;

import com.puduvandi.handover.entity.BikeConditionReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BikeConditionReviewRepository extends JpaRepository<BikeConditionReview, Long> {

    Optional<BikeConditionReview> findByBookingId(Long bookingId);

    boolean existsByBookingId(Long bookingId);
}
