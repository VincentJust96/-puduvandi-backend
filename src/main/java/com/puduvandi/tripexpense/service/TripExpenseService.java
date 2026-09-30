package com.puduvandi.tripexpense.service;

import com.puduvandi.auth.entity.User;
import com.puduvandi.auth.repository.UserRepository;
import com.puduvandi.common.enums.ExpenseCategory;
import com.puduvandi.exception.BusinessException;
import com.puduvandi.exception.ForbiddenException;
import com.puduvandi.exception.ResourceNotFoundException;
import com.puduvandi.tripexpense.dto.AddMemberRequest;
import com.puduvandi.tripexpense.dto.CreateExpenseRequest;
import com.puduvandi.tripexpense.dto.CreateTripRequest;
import com.puduvandi.tripexpense.dto.TripDashboardResponse;
import com.puduvandi.tripexpense.dto.TripDashboardResponse.CategoryBreakdown;
import com.puduvandi.tripexpense.dto.TripDashboardResponse.DailyPoint;
import com.puduvandi.tripexpense.dto.TripDashboardResponse.ExpenseFeedItem;
import com.puduvandi.tripexpense.dto.TripDashboardResponse.MapHotspot;
import com.puduvandi.tripexpense.dto.TripDashboardResponse.MemberBalance;
import com.puduvandi.tripexpense.dto.TripJoinResponse;
import com.puduvandi.tripexpense.dto.TripSummaryResponse;
import com.puduvandi.tripexpense.entity.Trip;
import com.puduvandi.tripexpense.entity.TripExpense;
import com.puduvandi.tripexpense.entity.TripExpenseSplit;
import com.puduvandi.tripexpense.entity.TripMember;
import com.puduvandi.tripexpense.repository.TripExpenseRepository;
import com.puduvandi.tripexpense.repository.TripExpenseSplitRepository;
import com.puduvandi.tripexpense.repository.TripMemberRepository;
import com.puduvandi.tripexpense.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * TripSplit: multi-traveler trip expense tracking and splitting. Deliberately
 * independent of the bike-rental role system — any authenticated user (even
 * one who hasn't picked CUSTOMER/OWNER yet) can create or join a trip, and
 * any member can add expenses or invite travelers.
 * <p>
 * Trip status (PLANNING / LIVE / SETTLED) and every dashboard figure are
 * derived on read rather than stored, so there is nothing to keep in sync.
 * Balances are pure paid-vs-fair-share — there is no separate settlement
 * ledger, so there is nothing that can go stale relative to the expenses
 * that produced it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripExpenseService {

    private static final String[] PALETTE = {"#3DDC97", "#5EB8FF", "#FFD166", "#FF8A5C", "#B79CFF", "#FF6B9C"};
    private static final BigDecimal EPSILON = new BigDecimal("0.01");

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripExpenseRepository tripExpenseRepository;
    private final TripExpenseSplitRepository tripExpenseSplitRepository;
    private final UserRepository userRepository;

    // ===== TRIPS =====

    @Transactional
    public TripSummaryResponse createTrip(Long userId, CreateTripRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException("Trip end date must be on or after the start date.");
        }

        Trip trip = tripRepository.save(Trip.builder()
                .name(request.name().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .budget(request.budget())
                .inviteToken(UUID.randomUUID().toString().replace("-", ""))
                .build());

        List<TripMember> members = new ArrayList<>();
        Set<String> seenPhones = new HashSet<>();
        members.add(TripMember.builder()
                .trip(trip)
                .user(creator)
                .phoneNumber(creator.getPhoneNumber() != null ? creator.getPhoneNumber() : "")
                .displayName(displayNameFor(creator))
                .colorHex(PALETTE[0])
                .creator(true)
                .contributedAmount(nonNegative(request.creatorContributedAmount()))
                .build());
        if (creator.getPhoneNumber() != null) seenPhones.add(creator.getPhoneNumber());

        int idx = 1;
        for (CreateTripRequest.MemberInput input : Optional.ofNullable(request.members()).orElse(List.of())) {
            String phone = normalizePhone(input.phoneNumber());
            if (phone == null || !seenPhones.add(phone)) continue;
            User existing = userRepository.findByPhoneNumberAndDeletedFalse(phone).orElse(null);
            members.add(TripMember.builder()
                    .trip(trip)
                    .user(existing)
                    .phoneNumber(phone)
                    .displayName(existing != null ? displayNameFor(existing) : phone)
                    .colorHex(PALETTE[idx % PALETTE.length])
                    .creator(false)
                    .contributedAmount(nonNegative(input.contributedAmount()))
                    .build());
            idx++;
        }
        tripMemberRepository.saveAll(members);

        log.info("Trip created: id={}, name={}, createdBy={}, members={}",
                trip.getId(), trip.getName(), userId, members.size());

        return new TripSummaryResponse(trip.getId(), trip.getName(), trip.getStartDate(), trip.getEndDate(),
                trip.getBudget(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), members.size(), "PLANNING", true);
    }

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> getMyTrips(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        linkPendingMemberships(user);

        return tripRepository.findAllForUser(userId).stream()
                .map(trip -> toSummary(trip, userId))
                .toList();
    }

    private TripSummaryResponse toSummary(Trip trip, Long userId) {
        List<TripMember> members = tripMemberRepository.findByTrip_IdOrderByIdAsc(trip.getId());
        List<TripExpense> expenses = tripExpenseRepository.findByTrip_IdOrderByExpenseDateDescCreatedAtDesc(trip.getId());
        BigDecimal spent = expenses.stream().map(TripExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        String status;
        if (expenses.isEmpty()) {
            status = "PLANNING";
        } else {
            Balances balances = computeBalances(members, expenses,
                    tripExpenseSplitRepository.findByExpense_Trip_Id(trip.getId()));
            status = deriveStatus(expenses, balances.balance());
        }

        boolean isCreator = members.stream()
                .anyMatch(m -> m.isCreator() && m.getUser() != null && m.getUser().getId().equals(userId));

        return new TripSummaryResponse(trip.getId(), trip.getName(), trip.getStartDate(), trip.getEndDate(),
                trip.getBudget(), spent.setScale(2, RoundingMode.HALF_UP), members.size(), status, isCreator);
    }

    // ===== DASHBOARD =====

    @Transactional(readOnly = true)
    public TripDashboardResponse getDashboard(Long userId, Long tripId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        linkPendingMemberships(user);

        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        List<TripMember> members = tripMemberRepository.findByTrip_IdOrderByIdAsc(tripId);
        TripMember me = members.stream()
                .filter(m -> m.getUser() != null && m.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenException("You are not a member of this trip."));

        List<TripExpense> expenses = tripExpenseRepository.findByTrip_IdOrderByExpenseDateDescCreatedAtDesc(tripId);
        List<TripExpenseSplit> splits = tripExpenseSplitRepository.findByExpense_Trip_Id(tripId);

        Balances b = computeBalances(members, expenses, splits);

        BigDecimal spent = expenses.stream().map(TripExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = trip.getBudget().subtract(spent);
        int budgetPct = trip.getBudget().signum() == 0 ? 0
                : Math.min(100, spent.multiply(BigDecimal.valueOf(100))
                        .divide(trip.getBudget(), 0, RoundingMode.HALF_UP).intValue());

        String status = deriveStatus(expenses, b.balance());

        int totalTripDays = (int) ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        LocalDate today = LocalDate.now();
        int dayOfTrip = Math.min(Math.max(1, (int) ChronoUnit.DAYS.between(trip.getStartDate(), today) + 1), totalTripDays);

        // Each traveler's equal share of the trip *budget* (what they're expected to have handed
        // over up front) — distinct from `owed`, which is their share of what's actually been spent.
        BigDecimal contributionShare = members.isEmpty() ? BigDecimal.ZERO
                : trip.getBudget().divide(BigDecimal.valueOf(members.size()), 2, RoundingMode.HALF_UP);

        List<MemberBalance> memberBalances = members.stream()
                .map(m -> {
                    BigDecimal contributed = m.getContributedAmount().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal due = contributionShare.subtract(contributed).max(BigDecimal.ZERO);
                    return new MemberBalance(m.getId(), m.getDisplayName(), m.getColorHex(),
                            b.paid().get(m.getId()).setScale(2, RoundingMode.HALF_UP),
                            b.owed().get(m.getId()).setScale(2, RoundingMode.HALF_UP),
                            b.balance().get(m.getId()),
                            contributed, contributionShare, due);
                })
                .toList();
        BigDecimal yourContribution = me.getContributedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal yourContributionDue = contributionShare.subtract(yourContribution).max(BigDecimal.ZERO);

        List<CategoryBreakdown> categories = categoryBreakdown(expenses, spent);
        List<ExpenseFeedItem> feed = expenseFeed(expenses, splits);
        List<DailyPoint> dailySeries = dailySeries(trip, expenses, today);
        List<MapHotspot> hotspots = mapHotspots(expenses);
        Long creatorMemberId = members.stream().filter(TripMember::isCreator).findFirst()
                .map(TripMember::getId).orElse(null);

        return new TripDashboardResponse(
                trip.getId(), trip.getName(), trip.getStartDate(), trip.getEndDate(), trip.getBudget(),
                spent, remaining.setScale(2, RoundingMode.HALF_UP), budgetPct, status, dayOfTrip, totalTripDays, members.size(),
                trip.getCoverImageUrl(), trip.getCoverImagePositionY(), trip.getInviteToken(),
                me.getId(), me.getDisplayName(), creatorMemberId,
                b.owed().get(me.getId()).setScale(2, RoundingMode.HALF_UP),
                b.paid().get(me.getId()).setScale(2, RoundingMode.HALF_UP),
                b.balance().get(me.getId()),
                yourContribution, contributionShare, yourContributionDue,
                memberBalances, categories, feed, dailySeries, hotspots
        );
    }

    @Transactional
    public void setCoverImage(Long userId, Long tripId, String imageUrl) {
        requireMembership(tripId, userId);
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        trip.setCoverImageUrl(imageUrl);
        trip.setCoverImagePositionY(50); // a new photo's old focal point is meaningless — reset to center
        tripRepository.save(trip);
    }

    @Transactional
    public void clearCoverImage(Long userId, Long tripId) {
        requireMembership(tripId, userId);
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        trip.setCoverImageUrl(null);
        trip.setCoverImagePositionY(50);
        tripRepository.save(trip);
    }

    @Transactional
    public void setCoverImagePosition(Long userId, Long tripId, int positionY) {
        requireMembership(tripId, userId);
        if (positionY < 0 || positionY > 100) {
            throw new BusinessException("Position must be between 0 and 100.");
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        if (trip.getCoverImageUrl() == null) {
            throw new BusinessException("This trip has no cover image to reposition.");
        }
        trip.setCoverImagePositionY(positionY);
        tripRepository.save(trip);
    }

    @Transactional
    public void deleteTrip(Long userId, Long tripId) {
        TripMember acting = requireMembership(tripId, userId);
        if (!acting.isCreator()) {
            throw new ForbiddenException("Only the trip creator can delete this trip.");
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        tripRepository.delete(trip);
        log.info("Trip deleted: id={}, deletedBy={}", tripId, userId);
    }

    // ===== MEMBERS =====

    /** Unlike trip creation's own invite loop, this endpoint requires the phone to already belong to a registered customer — no pending/placeholder members here, since the traveler is being added straight into an already-live trip. */
    @Transactional
    public void addMember(Long userId, Long tripId, AddMemberRequest request) {
        requireMembership(tripId, userId);
        String phone = normalizePhone(request.phoneNumber());
        if (phone == null) throw new BusinessException("Enter a valid phone number.");
        if (tripMemberRepository.existsByTrip_IdAndPhoneNumber(tripId, phone)) {
            throw new BusinessException("This phone number is already on the trip.");
        }

        Trip trip = tripRepository.getReferenceById(tripId);
        User existing = userRepository.findByPhoneNumberAndDeletedFalse(phone)
                .orElseThrow(() -> new BusinessException("No Puduvandi account found for this number yet — they'll need to sign up before you can add them."));
        long count = tripMemberRepository.countByTrip_Id(tripId);

        tripMemberRepository.save(TripMember.builder()
                .trip(trip)
                .user(existing)
                .phoneNumber(phone)
                .displayName(displayNameFor(existing))
                .colorHex(PALETTE[(int) (count % PALETTE.length)])
                .creator(false)
                .contributedAmount(nonNegative(request.contributedAmount()))
                .build());
    }

    /** Joining twice (e.g. clicking an old invite link again) is a no-op success rather than an error — simplest UX for "I'm already on this trip." */
    @Transactional
    public TripJoinResponse joinTripByToken(Long userId, String token) {
        Trip trip = tripRepository.findByInviteToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("This invite link is invalid or has expired."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        linkPendingMemberships(user);

        boolean alreadyMember = tripMemberRepository.findByTrip_IdAndUser_Id(trip.getId(), userId).isPresent();
        if (!alreadyMember) {
            long count = tripMemberRepository.countByTrip_Id(trip.getId());
            tripMemberRepository.save(TripMember.builder()
                    .trip(trip)
                    .user(user)
                    .phoneNumber(user.getPhoneNumber() != null ? user.getPhoneNumber() : "")
                    .displayName(displayNameFor(user))
                    .colorHex(PALETTE[(int) (count % PALETTE.length)])
                    .creator(false)
                    .contributedAmount(BigDecimal.ZERO)
                    .build());
            log.info("Trip joined via link: tripId={}, userId={}", trip.getId(), userId);
        }
        return new TripJoinResponse(trip.getId(), trip.getName());
    }

    /** Updates how much a traveler has contributed toward the trip budget so far — creator only, since this is a manual "I received their share" acknowledgement rather than something the member sets for themselves. */
    @Transactional
    public void updateMemberContribution(Long userId, Long tripId, Long memberId, BigDecimal contributedAmount) {
        TripMember acting = requireMembership(tripId, userId);
        if (!acting.isCreator()) {
            throw new ForbiddenException("Only the trip creator can update a traveler's contribution.");
        }
        TripMember target = tripMemberRepository.findById(memberId)
                .filter(m -> m.getTrip().getId().equals(tripId))
                .orElseThrow(() -> new ResourceNotFoundException("Trip member", memberId));

        target.setContributedAmount(nonNegative(contributedAmount).setScale(2, RoundingMode.HALF_UP));
        tripMemberRepository.save(target);
    }

    @Transactional
    public void removeMember(Long userId, Long tripId, Long memberId) {
        TripMember acting = requireMembership(tripId, userId);
        TripMember target = tripMemberRepository.findById(memberId)
                .filter(m -> m.getTrip().getId().equals(tripId))
                .orElseThrow(() -> new ResourceNotFoundException("Trip member", memberId));

        if (target.isCreator()) {
            throw new BusinessException("The trip creator can't be removed.");
        }
        if (!acting.isCreator() && !acting.getId().equals(memberId)) {
            throw new ForbiddenException("Only the trip creator can remove other members.");
        }

        boolean hasHistory = tripExpenseRepository.existsByTrip_IdAndPaidByMember_Id(tripId, memberId)
                || tripExpenseSplitRepository.existsByMember_Id(memberId);
        if (hasHistory) {
            throw new BusinessException("Can't remove — this member already has expense history on the trip.");
        }

        tripMemberRepository.delete(target);
    }

    // ===== EXPENSES =====

    @Transactional
    public void addExpense(Long userId, Long tripId, CreateExpenseRequest request) {
        requireMembership(tripId, userId);
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
        List<TripMember> members = tripMemberRepository.findByTrip_IdOrderByIdAsc(tripId);
        Map<Long, TripMember> byId = members.stream().collect(Collectors.toMap(TripMember::getId, m -> m));

        TripMember payer = byId.get(request.paidByMemberId());
        if (payer == null) throw new BusinessException("Selected payer is not a member of this trip.");

        List<Long> splitIds = Optional.ofNullable(request.splitMemberIds())
                .filter(l -> !l.isEmpty())
                .orElseGet(() -> members.stream().map(TripMember::getId).toList());
        List<TripMember> splitMembers = new ArrayList<>();
        for (Long id : splitIds) {
            TripMember m = byId.get(id);
            if (m == null) throw new BusinessException("One of the selected split members is not on this trip.");
            splitMembers.add(m);
        }
        if (splitMembers.isEmpty()) throw new BusinessException("Select at least one person to split with.");

        TripExpense expense = tripExpenseRepository.save(TripExpense.builder()
                .trip(trip)
                .title(request.title().trim())
                .amount(request.amount().setScale(2, RoundingMode.HALF_UP))
                .category(request.category())
                .paidByMember(payer)
                .location(request.location() != null && !request.location().isBlank() ? request.location().trim() : null)
                .expenseDate(request.expenseDate() != null ? request.expenseDate() : LocalDate.now())
                .build());

        tripExpenseSplitRepository.saveAll(buildEqualSplits(expense, splitMembers));

        log.info("Expense added: tripId={}, expenseId={}, amount={}, payer={}",
                tripId, expense.getId(), expense.getAmount(), payer.getId());
    }

    /** Equal split with any rounding leftover (a few cents) absorbed by the first member, so shares always sum exactly to the amount. */
    private List<TripExpenseSplit> buildEqualSplits(TripExpense expense, List<TripMember> splitMembers) {
        int count = splitMembers.size();
        BigDecimal base = expense.getAmount().divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
        BigDecimal remainder = expense.getAmount().subtract(base.multiply(BigDecimal.valueOf(count)));

        List<TripExpenseSplit> splits = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BigDecimal share = i == 0 ? base.add(remainder) : base;
            splits.add(TripExpenseSplit.builder().expense(expense).member(splitMembers.get(i)).shareAmount(share).build());
        }
        return splits;
    }

    @Transactional
    public void deleteExpense(Long userId, Long tripId, Long expenseId) {
        TripMember acting = requireMembership(tripId, userId);
        TripExpense expense = tripExpenseRepository.findById(expenseId)
                .filter(e -> e.getTrip().getId().equals(tripId))
                .orElseThrow(() -> new ResourceNotFoundException("Trip expense", expenseId));

        boolean isPayer = expense.getPaidByMember().getId().equals(acting.getId());
        if (!acting.isCreator() && !isPayer) {
            throw new ForbiddenException("Only the person who paid or the trip creator can delete this expense.");
        }

        tripExpenseRepository.delete(expense);
        log.info("Expense deleted: tripId={}, expenseId={}, deletedBy={}", tripId, expenseId, userId);
    }

    // ===== PRIVATE HELPERS =====

    private TripMember requireMembership(Long tripId, Long userId) {
        if (!tripRepository.existsById(tripId)) throw new ResourceNotFoundException("Trip", tripId);
        return tripMemberRepository.findByTrip_IdAndUser_Id(tripId, userId)
                .orElseThrow(() -> new ForbiddenException("You are not a member of this trip."));
    }

    /** Backfills the user link on any trip invite sitting pending under this phone number, and swaps the placeholder phone-number displayName for their real name. */
    private void linkPendingMemberships(User user) {
        if (user.getPhoneNumber() == null) return;
        List<TripMember> pending = tripMemberRepository.findByUserIsNullAndPhoneNumber(user.getPhoneNumber());
        if (pending.isEmpty()) return;
        for (TripMember m : pending) {
            m.setUser(user);
            m.setDisplayName(displayNameFor(user));
        }
        tripMemberRepository.saveAll(pending);
    }

    private record Balances(Map<Long, BigDecimal> paid, Map<Long, BigDecimal> owed, Map<Long, BigDecimal> balance) {}

    /** balance = paid - owed(fair share). Positive = they paid more than their share, so the group owes them. */
    private Balances computeBalances(List<TripMember> members, List<TripExpense> expenses, List<TripExpenseSplit> splits) {
        Map<Long, BigDecimal> paid = new HashMap<>();
        Map<Long, BigDecimal> owed = new HashMap<>();
        for (TripMember m : members) {
            paid.put(m.getId(), BigDecimal.ZERO);
            owed.put(m.getId(), BigDecimal.ZERO);
        }
        for (TripExpense e : expenses) paid.merge(e.getPaidByMember().getId(), e.getAmount(), BigDecimal::add);
        for (TripExpenseSplit s : splits) owed.merge(s.getMember().getId(), s.getShareAmount(), BigDecimal::add);

        Map<Long, BigDecimal> balance = new HashMap<>();
        for (TripMember m : members) {
            BigDecimal bal = paid.get(m.getId()).subtract(owed.get(m.getId()));
            balance.put(m.getId(), bal.setScale(2, RoundingMode.HALF_UP));
        }
        return new Balances(paid, owed, balance);
    }

    private String deriveStatus(List<TripExpense> expenses, Map<Long, BigDecimal> balance) {
        if (expenses.isEmpty()) return "PLANNING";
        boolean allSettled = balance.values().stream().allMatch(v -> v.abs().compareTo(EPSILON) <= 0);
        return allSettled ? "SETTLED" : "LIVE";
    }

    private List<CategoryBreakdown> categoryBreakdown(List<TripExpense> expenses, BigDecimal spent) {
        Map<ExpenseCategory, BigDecimal> byCategory = new EnumMap<>(ExpenseCategory.class);
        for (TripExpense e : expenses) byCategory.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        return byCategory.entrySet().stream()
                .sorted((a, c) -> c.getValue().compareTo(a.getValue()))
                .map(en -> new CategoryBreakdown(en.getKey().name(), en.getValue().setScale(2, RoundingMode.HALF_UP),
                        spent.signum() == 0 ? 0.0 : en.getValue().doubleValue() / spent.doubleValue() * 100))
                .toList();
    }

    private List<ExpenseFeedItem> expenseFeed(List<TripExpense> expenses, List<TripExpenseSplit> splits) {
        Map<Long, List<String>> namesByExpense = new HashMap<>();
        for (TripExpenseSplit s : splits) {
            namesByExpense.computeIfAbsent(s.getExpense().getId(), k -> new ArrayList<>()).add(s.getMember().getDisplayName());
        }
        return expenses.stream()
                .map(e -> new ExpenseFeedItem(
                        e.getId(), e.getTitle(), e.getAmount(), e.getCategory().name(), e.getExpenseDate(),
                        e.getPaidByMember().getId(), e.getPaidByMember().getDisplayName(), e.getLocation(),
                        namesByExpense.getOrDefault(e.getId(), List.of()), e.getCreatedAt()))
                .toList();
    }

    private List<DailyPoint> dailySeries(Trip trip, List<TripExpense> expenses, LocalDate today) {
        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (TripExpense e : expenses) byDate.merge(e.getExpenseDate(), e.getAmount(), BigDecimal::add);

        List<DailyPoint> series = new ArrayList<>();
        int dayNumber = 1;
        for (LocalDate d = trip.getStartDate(); !d.isAfter(trip.getEndDate()); d = d.plusDays(1), dayNumber++) {
            series.add(new DailyPoint(d, byDate.getOrDefault(d, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                    dayNumber, d.isAfter(today)));
        }
        return series;
    }

    /** Groups expenses by their free-text location. No real geocoding — x/y is a stable hash of the location name, purely for a spread-out visual layout. */
    private List<MapHotspot> mapHotspots(List<TripExpense> expenses) {
        Map<String, BigDecimal> byLocation = new LinkedHashMap<>();
        for (TripExpense e : expenses) {
            if (e.getLocation() == null || e.getLocation().isBlank()) continue;
            byLocation.merge(e.getLocation().trim(), e.getAmount(), BigDecimal::add);
        }
        return byLocation.entrySet().stream()
                .map(en -> {
                    int x = ((en.getKey() + "#x").hashCode() & Integer.MAX_VALUE) % 71 + 15;
                    int y = ((en.getKey() + "#y").hashCode() & Integer.MAX_VALUE) % 66 + 15;
                    return new MapHotspot(en.getKey(), en.getValue().setScale(2, RoundingMode.HALF_UP), x, y);
                })
                .toList();
    }

    private String displayNameFor(User u) {
        if (u.getFullName() != null && !u.getFullName().isBlank()) return u.getFullName();
        if (u.getPhoneNumber() != null) return u.getPhoneNumber();
        return u.getEmail();
    }

    /** Treats a missing contribution as zero rather than requiring every caller to supply one. */
    private BigDecimal nonNegative(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    /** Lenient parse: strips everything but digits, keeps the last 10 (drops a leading country code like 91). */
    private String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
        return digits.isEmpty() ? null : digits;
    }
}
