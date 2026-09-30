package com.puduvandi.tripexpense.repository;

import com.puduvandi.tripexpense.entity.TripExpenseSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripExpenseSplitRepository extends JpaRepository<TripExpenseSplit, Long> {

    List<TripExpenseSplit> findByExpense_Trip_Id(Long tripId);

    List<TripExpenseSplit> findByExpense_Id(Long expenseId);

    boolean existsByMember_Id(Long memberId);
}
