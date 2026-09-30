package com.puduvandi.tripexpense.repository;

import com.puduvandi.tripexpense.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, Long> {

    /** All trips the given user is a member of, most recently created first. */
    @Query("""
        SELECT DISTINCT m.trip FROM TripMember m
        WHERE m.user.id = :userId
        ORDER BY m.trip.createdAt DESC
        """)
    List<Trip> findAllForUser(@Param("userId") Long userId);

    Optional<Trip> findByInviteToken(String inviteToken);
}
