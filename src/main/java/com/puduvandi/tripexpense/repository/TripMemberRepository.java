package com.puduvandi.tripexpense.repository;

import com.puduvandi.tripexpense.entity.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripMemberRepository extends JpaRepository<TripMember, Long> {

    List<TripMember> findByTrip_IdOrderByIdAsc(Long tripId);

    Optional<TripMember> findByTrip_IdAndUser_Id(Long tripId, Long userId);

    boolean existsByTrip_IdAndUser_Id(Long tripId, Long userId);

    boolean existsByTrip_IdAndPhoneNumber(Long tripId, String phoneNumber);

    long countByTrip_Id(Long tripId);

    /** Invited members not yet linked to an account — backfilled once that phone number logs in. */
    List<TripMember> findByUserIsNullAndPhoneNumber(String phoneNumber);
}
