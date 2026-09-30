package com.puduvandi.tripexpense.repository;

import com.puduvandi.tripexpense.entity.TripExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripExpenseRepository extends JpaRepository<TripExpense, Long> {

    List<TripExpense> findByTrip_IdOrderByExpenseDateDescCreatedAtDesc(Long tripId);

    boolean existsByTrip_IdAndPaidByMember_Id(Long tripId, Long memberId);
}
