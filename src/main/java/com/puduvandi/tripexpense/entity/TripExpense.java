package com.puduvandi.tripexpense.entity;

import com.puduvandi.common.entity.BaseEntity;
import com.puduvandi.common.enums.ExpenseCategory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A single spend on a {@link Trip}, paid by one member and split across others (see {@link TripExpenseSplit}). */
@Entity
@Table(name = "trip_expenses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripExpense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paid_by_member_id", nullable = false)
    private TripMember paidByMember;

    /** Free-text spend location, used to group the trip's expense map — optional. */
    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;
}
