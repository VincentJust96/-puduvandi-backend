package com.puduvandi.tripexpense.dto;

import com.puduvandi.common.enums.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Request to log a new expense on a trip")
public record CreateExpenseRequest(

    @NotBlank(message = "Title is required")
    @Schema(example = "Sunset cruise tickets")
    String title,

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Schema(example = "2100")
    BigDecimal amount,

    @NotNull(message = "Category is required")
    @Schema(example = "FOOD")
    ExpenseCategory category,

    @NotNull(message = "paidByMemberId is required")
    @Schema(example = "1")
    Long paidByMemberId,

    @Schema(description = "Defaults to today when absent", example = "2026-08-28")
    LocalDate expenseDate,

    @Schema(description = "Free-text spend location, shown on the expense map", example = "Baga")
    String location,

    @Schema(description = "Member IDs sharing this expense — defaults to every current trip member when absent/empty")
    List<Long> splitMemberIds

) {}
