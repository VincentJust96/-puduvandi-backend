package com.puduvandi.review.repository;

import com.puduvandi.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    /** Null when the bike has no reviews yet — callers should treat that as "no rating". */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.bike.id = :bikeId")
    Double averageRatingForBike(@Param("bikeId") Long bikeId);

    /**
     * Batch version of averageRatingForBike for a page of bikes — one query instead of
     * one-per-bike when rendering a paginated bike listing.
     */
    @Query("SELECT r.bike.id AS bikeId, AVG(r.rating) AS avgRating FROM Review r WHERE r.bike.id IN :bikeIds GROUP BY r.bike.id")
    List<BikeAverageRating> averageRatingsForBikes(@Param("bikeIds") List<Long> bikeIds);

    /** Batch version of existsByBookingId for a page of bookings — same N+1 concern as above. */
    @Query("SELECT r.booking.id FROM Review r WHERE r.booking.id IN :bookingIds")
    Set<Long> findReviewedBookingIds(@Param("bookingIds") List<Long> bookingIds);

    /** Public review listing for a bike's detail page, newest first. */
    Page<Review> findByBikeIdOrderByCreatedAtDesc(Long bikeId, Pageable pageable);

    /** Admin moderation listing, newest first. */
    Page<Review> findAllByOrderByCreatedAtDesc(Pageable pageable);

    interface BikeAverageRating {
        Long getBikeId();
        Double getAvgRating();
    }
}
