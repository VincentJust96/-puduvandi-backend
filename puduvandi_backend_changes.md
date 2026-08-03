# Puduvandi Backend — Code Review Changes

This document lists every file changed during this session's backend code review, what was changed, and why. Organized by area. All changes were verified with `mvn test` (114/114 passing) after each batch; a live server start was also performed to confirm Spring wiring (self-injection, `@Value` field injection) is correct.

---

## 1. Broken tests (pre-existing, fixed first)

### `src/test/java/com/puduvandi/booking/BookingServiceTransitionTest.java`
- **Changed:** Added a `ReviewRepository` `@Mock` field and passed it as the final constructor argument to `new BookingService(...)`.
- **Why:** `BookingService`'s constructor had gained a `ReviewRepository` parameter in an earlier change, but this test's constructor call was never updated — it failed to compile (`mvn test-compile` error: "constructor BookingService cannot be applied to given types").

### `src/test/java/com/puduvandi/admin/service/AdminServiceDeliveryRateTest.java`
- **Changed:** Added a `ReviewRepository` `@Mock` field and constructor argument (later also `DeliveryOrderRepository`, see §4).
- **Why:** Same class of compile failure as above — `AdminService`'s constructor had gained a `ReviewRepository` parameter that this test never picked up.

---

## 2. Security fixes — soft-delete / unique-constraint regressions

### `src/main/java/com/puduvandi/auth/repository/UserRepository.java`
- **Changed:** Added `existsByPhoneNumberAndDeletedFalse(String)` and `findByEmailIgnoreCase(String)` (deleted-blind lookup, as opposed to the existing `AndDeletedFalse` variant).
- **Why:** Needed by the two fixes below.

### `src/main/java/com/puduvandi/user/service/UserService.java`
- **Changed:** `ensurePhoneNotTaken()` now calls `existsByPhoneNumberAndDeletedFalse` instead of the deleted-blind `existsByPhoneNumber`.
- **Why:** `phone_number` has a plain (non-partial) unique constraint. Once a user is soft-deleted, `existsByPhoneNumber` still saw their row and permanently blocked anyone else (including OTP-based reactivation flows) from ever using that number for phone-add/phone-change. This is the same bug class already fixed once in `AuthService.sendOtp` — it had crept back in via `UserService`.

### `src/main/java/com/puduvandi/auth/service/AuthService.java`
- **Changed:** `emailSignup()` now looks up the email via `findByEmailIgnoreCase` (deleted-blind) and, if a soft-deleted account is found, reactivates it (clears `deleted`, resets password hash/role/status/kycStatus) instead of always throwing `ConflictException`.
- **Why:** Mirrors `verifyOtp()`'s existing reactivate-on-recreate behavior for the phone/OTP flow. Previously, a soft-deleted email/password account could never sign up again with that email — permanently locked out, inconsistent with how the phone flow already handles this.

### `src/main/java/com/puduvandi/partner/repository/PartnerProfileRepository.java`
- **Changed:** Added `findByUserId(Long)` (deleted-blind, alongside the existing `findByUserIdAndDeletedFalse`).

### `src/main/java/com/puduvandi/partner/service/PartnerProfileService.java`
- **Changed:** `findOrCreatePartnerProfile()` now checks for an existing (possibly soft-deleted) profile via `findByUserId` and reactivates it if found, instead of always going straight to `orElseGet(() -> ... save(new profile))`.
- **Why:** `partner_profiles.user_id` has a plain unique constraint. If an admin soft-deletes a partner (`AdminService.deletePartner`, which only flips `is_deleted` — the `User` row stays active), any subsequent call to `GET /partner/me` or document upload tried to INSERT a new row with the same `user_id` and hit a `DataIntegrityViolationException` → 500. `OwnerService` already had this exact reactivation fix; `PartnerProfileService` never got the equivalent (it's a structurally parallel service, and the fix wasn't ported over).

---

## 3. Security fix — push subscription IDOR

### `src/main/java/com/puduvandi/push/repository/PushSubscriptionRepository.java`
- **Changed:** Added `deleteByEndpointAndUser_Id(String, Long)`.

### `src/main/java/com/puduvandi/push/service/WebPushService.java`
- **Changed:** `unsubscribe(String endpoint)` → `unsubscribe(Long userId, String endpoint)`, now deletes scoped to the caller's own `userId`.

### `src/main/java/com/puduvandi/push/controller/PushController.java`
- **Changed:** `unsubscribe` endpoint now passes `principal.getUserId()` through to the service.

### `src/test/java/com/puduvandi/push/service/WebPushServiceTest.java`
- **Changed:** Updated the `unsubscribe` test to call the new two-arg signature and assert `deleteByEndpointAndUser_Id` is called (was `deleteByEndpoint`).

- **Why (all four files):** `DELETE /api/v1/push/subscribe?endpoint=...` deleted by endpoint string alone, with no check that the endpoint belonged to the authenticated caller. Any authenticated user who learned or guessed another user's push-subscription endpoint could silently kill that other user's push notifications (IDOR).

---

## 4. Financial correctness — deposit refunds & concurrency

### `src/main/java/com/puduvandi/payment/service/PaymentService.java`
- **Changed:**
  1. Added a lazily-autowired self-reference field (`@Autowired @Lazy private PaymentService self;`).
  2. `refundDeposit(...)` is now `@Transactional(propagation = Propagation.REQUIRES_NEW)` instead of plain `@Transactional`.
  3. In the `RazorpayException` catch block, `booking.getCustomer().getId()` is now null-guarded (`booking.getCustomer() != null ? ... : null`).
  4. `releaseUnclaimedDeposits(...)` is no longer itself `@Transactional`, and its loop now calls `self.refundDeposit(...)` (through the Spring proxy) instead of `refundDeposit(...)` (a plain self-invocation, which would have bypassed the `REQUIRES_NEW` annotation entirely).
- **Why:**
  - `refundDeposit` already calls the real (irreversible) Razorpay refund API before any DB write. Under the original `@Transactional` (REQUIRED) propagation, calling it from `releaseUnclaimedDeposits`'s batch loop meant one booking's exception could roll back the whole enclosing transaction — including earlier bookings in the same batch whose Razorpay refunds had *already succeeded*, reverting their DB status from `REFUNDED` back to `HELD`. The next scheduled run would then refund them a second time.
  - Making `refundDeposit` `REQUIRES_NEW` fixes this for both the batch loop and for `DepositClaimService.approveClaim`/`rejectClaim` (a later step in either method throwing would no longer un-commit an already-successful refund).
  - Self-invocation (`this.refundDeposit(...)`) does not go through Spring's proxy, so the `REQUIRES_NEW` annotation is silently ignored on self-invoked calls — the `self` field routes the call through the proxy so the isolation actually takes effect.
  - The `booking.getCustomer()` null-guard prevents an `NPE` inside the failure-handling path from breaking the method's own "never throws past the caller" contract.

### `src/test/java/com/puduvandi/payment/PaymentServiceTest.java`
- **Changed:** After constructing `paymentService`, wires the `self` field to the same instance via `ReflectionTestUtils.setField(...)`.
- **Why:** In a plain Mockito unit test there is no Spring proxy, so `self` would otherwise be `null` and `releaseUnclaimedDeposits` would NPE.

### `src/main/java/com/puduvandi/booking/repository/BookingRepository.java`
- **Changed:** Added a pessimistic-write `lockById(Long)` (mirrors the existing `BikeRepository`/`DeliveryOrderRepository` lock pattern).

### `src/main/java/com/puduvandi/deposit/repository/DepositClaimRepository.java`
- **Changed:** Added a pessimistic-write `lockById(Long)`.

### `src/main/java/com/puduvandi/deposit/service/DepositClaimService.java`
- **Changed:**
  1. `fileClaim(...)` now locks the booking row (`bookingRepository.lockById`) before checking `depositStatus`/`status`, instead of relying only on the earlier ownership lookup.
  2. `findPendingClaim(...)` (used by `approveClaim`/`rejectClaim`) now calls `depositClaimRepository.lockById` instead of plain `findById`.
- **Why:** Both were check-then-act races: two concurrent `fileClaim` calls (or two concurrent `approveClaim`/`rejectClaim` calls — e.g. an admin double-click or a retried request) could both pass the "not yet claimed"/"still PENDING" check before either transaction committed, resulting in two claims filed against one booking, or the same claim being approved and rejected simultaneously (double refund risk).

### `src/test/java/com/puduvandi/deposit/service/DepositClaimServiceTest.java`
- **Changed:** Added `bookingRepository.lockById(...)` stubs alongside the existing `findByIdAndOwner_UserIdAndDeletedFalse` stubs in the `fileClaim` tests; renamed `depositClaimRepository.findById` stubs to `lockById` in the `approveClaim`/`rejectClaim` tests.
- **Why:** Required to match the new repository calls above.

### `src/main/java/com/puduvandi/handover/repository/HandoverOtpRepository.java`
- **Changed:** Added `lockLatestActive(...)` — same query as the existing `findLatestActive` but with a pessimistic write lock.

### `src/main/java/com/puduvandi/handover/service/HandoverOtpService.java`
- **Changed:** `verify(...)` now calls `lockLatestActive` instead of `findLatestActive`.
- **Why:** Without a lock, two near-simultaneous correct-code verify calls for the same booking+purpose could both pass validation and both fire the handover's state transition (double-fire risk).

### `src/test/java/com/puduvandi/handover/HandoverOtpServiceTest.java`
- **Changed:** Renamed all `findLatestActive` mock references to `lockLatestActive`.
- **Why:** Required to match the repository method rename/addition above.

---

## 5. Centralized exception handling

### `src/main/java/com/puduvandi/exception/GlobalExceptionHandler.java`
- **Changed:** Added an `@ExceptionHandler(DataIntegrityViolationException.class)` returning HTTP 409 with a friendly message (and logging via `ErrorLogService`, same as the existing 404/500 handlers).
- **Why:** Several check-then-act races (concurrent review submissions for the same booking, concurrent phone/email signups) can slip past the app-level guard and hit a DB unique/FK constraint instead. Previously this fell through to the generic `Exception` handler and surfaced as a raw 500; now it's reported as the intended 409.

---

## 6. Admin module fixes

### `src/main/java/com/puduvandi/admin/service/AdminService.java`
- **Changed:**
  1. Added a `DeliveryOrderRepository` constructor dependency.
  2. `deletePartner(...)` now calls a new `assertNoActiveDeliveries(...)` guard (counts `CLAIMED`/`PICKED_UP` delivery orders for the partner) before soft-deleting, mirroring the existing `assertNoActiveOwnerBookings` guard already used by `deleteOwner(...)`.
  3. Added a `@Value("${PUDUVANDI_ENV:}") private String puduvandiEnv;` field, and `resetLocalData()` now reads this instead of calling `System.getenv("PUDUVANDI_ENV")` directly.
  4. `RESET_ALLOWED_ENVS` narrowed from `Set.of("", "local")` to `Set.of("local")` — an unset/blank `PUDUVANDI_ENV` is now **disallowed** (fail-closed), not implicitly treated as local.
  5. Added `listReviews(...)`, `deleteReview(...)`, and a `toReviewResponse(...)` mapper (review moderation — see §8).
  6. `toBikeResponse(...)` split into a single-bike overload (unchanged behavior) and a new `toBikeResponsePage(Page<Bike>)` that batches the per-bike trip-count and average-rating lookups into two queries total instead of one-per-bike (N+1 fix — see §7 for the matching repository changes). `listBikes(...)` now calls `toBikeResponsePage(...)`.
  7. `updateBike(...)`: the `papersIncluded`/`fuelIncluded`/`roadsideAssistance` setters are now null-guarded (only applied if the incoming `Boolean` is non-null), instead of being unconditionally overwritten on every edit.
- **Why:**
  - (2) `deletePartner` had no protection against deleting a partner mid-delivery, unlike the equivalent owner-delete path — an admin could orphan an in-flight delivery order.
  - (3)/(4) `resetLocalData` is a destructive endpoint (truncates almost the entire schema) that previously treated an *unset* `PUDUVANDI_ENV` as implicitly local/allowed. Every real deployment for this project (staging included) sets `PUDUVANDI_ENV` explicitly, so there was no legitimate workflow depending on the blank case — leaving it open only meant a deployment that forgot to set the var would have this endpoint silently enabled. The `@Value` field also makes the env check unit-testable (see `AdminServiceResetLocalDataTest`, §9), where `System.getenv()` could not be.
  - (6) Admin's bike-listing endpoint ran 2 extra queries per row (booking count + average rating) on every page — with a default page size, this was dozens of extra queries per request.
  - (7) A client that omits these three boolean fields on a PUT (e.g. an un-updated frontend build) would otherwise silently flip an existing `true` flag to `false` on every edit.

### `src/main/java/com/puduvandi/superadmin/controller/SuperAdminController.java`
- **Changed:** Updated the `@Operation` summary text for `POST /reset-local-data` from "Blocked unless PUDUVANDI_ENV is unset or \"local\"" to "Blocked unless PUDUVANDI_ENV is exactly \"local\" — never unset, staging, or production."
- **Why:** Documentation now matches the tightened behavior in `AdminService` above.

### `src/test/java/com/puduvandi/admin/service/AdminServiceResetLocalDataTest.java` (new file)
- **Added:** Unit tests covering: unset env blocked, blank env blocked, staging/production blocked, wrong confirmation phrase rejected, valid local+phrase request wipes data and returns the count, and case/whitespace-insensitivity of the env value.
- **Why:** `resetLocalData` — a destructive endpoint wiping most of the schema — had **zero** test coverage before this session, despite being explicitly flagged as a priority in the review request.

---

## 7. Bike listing — N+1 query fixes & new-field validation

### `src/main/java/com/puduvandi/booking/repository/BookingRepository.java`
- **Changed:** Added `countCompletedTripsForBikes(List<Long> bikeIds, BookingStatus status)` (a `GROUP BY` query) and a `BikeTripCount` projection interface (`getBikeId()`, `getTripCount()`).

### `src/main/java/com/puduvandi/review/repository/ReviewRepository.java`
- **Changed:** Added `averageRatingsForBikes(List<Long> bikeIds)` (a `GROUP BY` query) with a `BikeAverageRating` projection (`getBikeId()`, `getAvgRating()`); added `findReviewedBookingIds(List<Long> bookingIds)` returning a `Set<Long>`; also added `findByBikeIdOrderByCreatedAtDesc(...)` and `findAllByOrderByCreatedAtDesc(...)` (used by the new review-listing feature, §8).

### `src/main/java/com/puduvandi/bike/service/BikeService.java`
- **Changed:**
  1. `toResponse(Bike)` kept as a single-bike convenience method (unchanged behavior/callers), now delegating to a new `toResponse(Bike, long totalTrips, Double rating)` core method.
  2. Added `toResponsePage(Page<Bike>)`, which batches the trip-count and rating lookups for an entire page into two queries (using the new repository methods above) instead of one-per-bike.
  3. `getMyBikes(...)` and `browseAvailableBikes(...)` now call `toResponsePage(...)` instead of `.map(this::toResponse)`.
  4. `addBike(...)`: `papersIncluded`/`fuelIncluded`/`roadsideAssistance` now default to `true` when the incoming request field is `null` (previously always passed through, defaulting to Java's primitive-`boolean` `false` when a client omitted the field).
  5. `updateBike(...)`: same three fields are now null-guarded (only applied if non-null) instead of unconditionally overwritten.
- **Why:**
  - (2)/(3) `browseAvailableBikes` (the public bike-browsing endpoint) and `getMyBikes` each ran 2 extra queries per bike in the page — a real, measurable N+1 that scales with listing traffic.
  - (4) The V36 migration added these three columns with `DEFAULT true` specifically so existing bikes keep showing the same "included" chips — but every *new* bike went through the DTO's primitive `boolean` fields, which silently default to `false` when a client (e.g. an un-updated mobile build) omits them, contradicting the product intent.
  - (5) Same class of bug as `AdminService.updateBike` above — a client omitting these fields on a PUT would silently flip existing `true` flags to `false`.

### `src/main/java/com/puduvandi/bike/dto/AddBikeRequest.java`
- **Changed:** `papersIncluded`, `fuelIncluded`, `roadsideAssistance` changed from primitive `boolean` to boxed `Boolean` (so "omitted" can be told apart from "explicitly false"); added `@Future(message = "Insurance expiry date must be in the future")` on `insuranceExpiryDate`.

### `src/main/java/com/puduvandi/bike/dto/UpdateBikeRequest.java`
- **Changed:** Same `boolean` → `Boolean` change for the three flags; same `@Future` validation added on `insuranceExpiryDate`.
- **Why:** An owner could previously submit (or leave in place, on update) an already-expired insurance date with no server-side check, and it would be shown to customers as valid documentation.

### `src/main/java/com/puduvandi/booking/service/BookingService.java`
- **Changed:** `toResponse(Booking)` kept as a convenience method delegating to a new `toResponse(Booking, boolean reviewed)`; added `toResponsePage(Page<Booking>)` which batches the "already reviewed?" lookup (via the new `findReviewedBookingIds`) for an entire page into one query; `getMyBookings(...)` and `getOwnerBookings(...)` now call `toResponsePage(...)`.
- **Why:** The newly-added `reviewRepository.existsByBookingId(...)` call (checking whether a completed booking has been reviewed yet, for display purposes) ran once per row on every paginated booking-history call — another N+1 introduced alongside the review feature.

---

## 8. Review feature — was write-only, now readable + moderatable

*(This was flagged as a business-logic gap: reviews could be submitted but never listed or moderated anywhere.)*

### `src/main/java/com/puduvandi/review/dto/BikeReviewResponse.java` (new file)
- **Added:** Public-facing review DTO (`id`, `customerName`, `rating`, `comment`, `createdAt`) for the bike-listing page.

### `src/main/java/com/puduvandi/review/service/ReviewService.java`
- **Changed:** Added `listReviewsForBike(Long bikeId, int page, int size)` and a `toBikeReviewResponse(...)` mapper that shortens the customer's full name to "First L." for a little privacy on a public listing (`firstNameAndInitial(...)` helper).

### `src/main/java/com/puduvandi/bike/controller/BikeController.java`
- **Changed:** Added `GET /api/v1/bikes/{id}/reviews` (public, paginated) alongside the existing bike-detail endpoints; injected `ReviewService`.
- **Why:** A bike's average rating was already shown on its listing, but the individual review comments a customer submitted were never surfaced anywhere — the feature was effectively half-built.

### `src/main/java/com/puduvandi/admin/dto/AdminReviewResponse.java` (new file)
- **Added:** Admin-facing review DTO including `bookingId`, `bikeId`, `bikeLabel`, `customerId`, `customerName` (full, unshortened), `rating`, `comment`, `createdAt`.

### `src/main/java/com/puduvandi/admin/controller/AdminController.java`
- **Changed:** Added `GET /api/v1/admin/reviews` (paginated list) and `DELETE /api/v1/admin/reviews/{reviewId}` (moderation — hard delete, since `Review` has no soft-delete concept; the customer is then free to resubmit for that booking if warranted).
- **Why:** There was previously no way for an admin to see or remove an abusive/fake review, even though its rating fed directly into the public-facing bike listing.

### `src/test/java/com/puduvandi/review/service/ReviewServiceTest.java` (new file)
- **Added:** Unit tests for `submitReview` (booking-not-found, wrong-customer/forbidden, not-completed, already-reviewed/conflict, valid-submission) and `listReviewsForBike` (name-shortening).
- **Why:** `ReviewService` (a brand-new, entirely uncommitted module going into this review) had zero test coverage.

---

## 9. Owner/Partner KYC — rejected accounts could never resubmit

### `src/main/java/com/puduvandi/owner/service/OwnerService.java`
- **Changed:** `uploadDocument(...)` now transitions `kycStatus` to `PENDING` when it was either `NOT_SUBMITTED` **or** `REJECTED` (previously only `NOT_SUBMITTED`).

### `src/main/java/com/puduvandi/partner/service/PartnerProfileService.java`
- **Changed:** Same fix — `uploadDocument(...)` now re-queues from `REJECTED` as well as `NOT_SUBMITTED`.
- **Why:** After an admin rejects an owner's or partner's KYC (`AdminService.rejectOwnerKyc`/`rejectPartnerKyc`, which set `kycStatus = REJECTED`), the corresponding `approve*Kyc` methods only accept `kycStatus == PENDING`. With no code path ever moving `REJECTED` back to `PENDING`, a rejected owner/partner who re-uploaded corrected documents had no way to get back onto the admin review queue — a permanent dead end.

---

## 10. Rate limiting — per-phone bucket had no aggregate IP cap

### `src/main/java/com/puduvandi/security/RateLimitFilter.java`
- **Changed:** `applyBodyPhoneLimit(...)` now also consumes a per-IP aggregate bucket (30/15min for send-otp, 60/15min for verify-otp) **in addition to** the existing per-phone-number bucket, before checking the phone-specific limit.
- **Why:** The rate limiter previously keyed its bucket purely on the `phoneNumber` field in the request body. An attacker rotating through many different phone numbers from one source got a fresh 5-or-10-request bucket per number, with no ceiling on the total from that source — defeating the limiter's purpose for SMS-cost-abuse/enumeration scenarios (single-number brute-force was already correctly capped; only the cross-number aggregate was missing).

---

## 11. CORS — hardcoded shared-domain wildcard made configurable

### `src/main/java/com/puduvandi/config/CorsConfig.java`
- **Changed:** The `https://*.vercel.app` origin pattern (a shared public domain anyone can deploy under, combined with `allowCredentials(true)`) is now sourced from a `CORS_EXTRA_ORIGINS` env var (comma-separated), defaulting to the same `https://*.vercel.app` value to preserve current staging behavior exactly.

### `.env.example`
- **Changed:** Documented the new `CORS_EXTRA_ORIGINS` variable with guidance to set it to the real frontend domain before going to production.
- **Why:** This wildcard was already a known, documented trade-off for the current test/staging deployment (see project deployment notes: "fine for test — must be tightened to the exact prod domain before going live"). This change doesn't alter current (staging) behavior at all — it just makes tightening it for production a one-line env var change instead of a code change, so it doesn't get forgotten.

---

## Test suite status

All changes were verified with `mvn test` after each batch. Final run: **114 tests, 0 failures, 0 errors** (up from 102 at the start of the session — the two previously-broken test files plus 12 new tests: `AdminServiceResetLocalDataTest` ×6, `ReviewServiceTest` ×6).

A live server start (`mvn spring-boot:run` against the local Postgres instance) was also performed to confirm the Spring wiring changes — particularly `PaymentService`'s self-injection and `AdminService`'s `@Value` field — initialize correctly; the application context started cleanly and an OTP login flow was exercised successfully before this document was requested.

## Known remaining items (not fixed this session — flagged for a follow-up)

- `BookingService.toResponse`'s pre-existing `customerLicenceUrl(...)` per-row lookup is a similar N+1 to the ones fixed above, but predates this review and wasn't part of the newly-introduced regressions — left as-is to avoid scope creep on a helper used elsewhere.
- No persisted audit trail exists for admin/superadmin destructive actions (KYC decisions, deletes, `resetLocalData`, etc.) — currently only `log.info`/`log.warn`, not a queryable table. Flagged by the audit as worth adding but out of scope for this pass.
- Deposit-claim `fileClaim` still has no DB-level unique constraint limiting one active claim per booking (only the new row-lock guard) — a partial unique index would be a stronger, complementary fix.
