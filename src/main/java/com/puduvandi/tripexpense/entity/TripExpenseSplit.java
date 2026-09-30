package com.puduvandi.tripexpense.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** One member's share of a {@link TripExpense}. */
@Entity
@Table(name = "trip_expense_splits")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripExpenseSplit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id", nullable = false)
    private TripExpense expense;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private TripMember member;

    @Column(name = "share_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal shareAmount;
}
