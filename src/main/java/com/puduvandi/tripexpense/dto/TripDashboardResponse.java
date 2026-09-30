package com.puduvandi.tripexpense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything the trip dashboard page needs in one call: hero/budget stats,
 * per-member balances, category breakdown, the expense feed, a daily spend
 * series (used for both the "Daily" pulse chart tab and the travel
 * timeline), and location-grouped map hotspots.
 */
public record TripDashboardResponse(
    Long tripId,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal budget,
    BigDecimal spent,
    BigDecimal remaining,
    int budgetPct,
    String status,
    int dayOfTrip,
    int totalTripDays,
    int travelers,
    String coverImageUrl,
    int coverImagePositionY,
    String inviteToken,

    Long currentMemberId,
    String currentMemberName,
    Long creatorMemberId,
    BigDecimal yourShare,
    BigDecimal youPaid,
    BigDecimal youBalance,
    BigDecimal yourContribution,
    BigDecimal yourContributionShare,
    BigDecimal yourContributionDue,

    List<MemberBalance> members,
    List<CategoryBreakdown> categories,
    List<ExpenseFeedItem> expenses,
    List<DailyPoint> dailySeries,
    List<MapHotspot> hotspots
) {
    public record MemberBalance(
        Long memberId,
        String name,
        String colorHex,
        BigDecimal paid,
        BigDecimal owed,
        BigDecimal balance,
        BigDecimal contributedAmount,
        BigDecimal contributionShare,
        BigDecimal contributionDue
    ) {}

    public record CategoryBreakdown(
        String category,
        BigDecimal amount,
        double pct
    ) {}

    public record ExpenseFeedItem(
        Long id,
        String title,
        BigDecimal amount,
        String category,
        LocalDate expenseDate,
        Long paidByMemberId,
        String paidByName,
        String location,
        List<String> splitMemberNames,
        LocalDateTime createdAt
    ) {}

    public record DailyPoint(
        LocalDate date,
        BigDecimal amount,
        int dayNumber,
        boolean upcoming
    ) {}

    public record MapHotspot(
        String location,
        BigDecimal amount,
        int xPct,
        int yPct
    ) {}
}
