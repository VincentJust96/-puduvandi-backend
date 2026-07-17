# Puduvandi — Tourism Mobility Marketplace

A backend API for a bike rental marketplace where customers can discover and instantly book bikes listed by owners. Built with Java 21 + Spring Boot 3.2.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Database | PostgreSQL 16 |
| Migrations | Flyway 9 |
| ORM | Spring Data JPA + Hibernate |
| Auth | JWT (JJWT 0.12.5) + Phone OTP |
| Mapping | MapStruct 1.5.5 |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven |

---

## Architecture

Modular monolith. Each domain is a self-contained package under `com.puduvandi`.

```
com.puduvandi/
├── auth/          # OTP login, JWT issue/refresh/logout, User entity
├── user/          # Customer profile, document upload
├── owner/         # Owner KYC profile, bike management, dashboard
├── bike/          # Bike listings, availability toggle
├── booking/       # Instant booking, ride lifecycle, price estimate
├── admin/         # Dashboard, user management, KYC approval, commission
├── common/        # Shared enums, BaseEntity, ApiResponse wrapper
├── security/      # JwtAuthenticationFilter, JwtUtil, UserPrincipal
├── config/        # SecurityConfig, CorsConfig, SwaggerConfig, JPA audit
└── exception/     # GlobalExceptionHandler, typed exceptions
```

---

## Key Business Rules

- **Instant booking**: Customers book directly — no owner approval step.
- **Booking flow**: `CREATED → CONFIRMED → RIDE_STARTED → RETURN_REQUESTED → COMPLETED`
- **Commission**: Platform takes a configurable % (default 20%) from each booking.
- **OTP auth**: Phone-number only login. No passwords.
- **Mock OTP**: In dev mode, the OTP is always `123456` (set `otp.mock-enabled: true`).

---

## Database Migrations (Flyway)

| Version | File | What it creates / changes |
|---|---|---|
| V1 | `V1__create_users_and_auth_tables.sql` | `users`, `otp_records`, `refresh_tokens` |
| V2 | `V2__create_owner_and_document_tables.sql` | `owner_profiles`, `owner_documents`, `user_documents` |
| V3 | `V3__create_bike_and_booking_tables.sql` | `bikes`, `bike_images`, `bookings` |
| V4 | `V4__create_commission_settings_table.sql` | `commission_settings` (seeded with 20%) |
| V5 | `V5__convert_enum_columns_to_varchar.sql` | Converts all PostgreSQL custom enum type columns to `VARCHAR(50)` |

> **Note:** `baseline-version: 3` is set in `application.yml` because V1–V3 tables were created before Flyway tracking was enabled. Flyway baselines at V3 and only applies V4+ on a fresh-tracked DB.

> **Why V5 exists:** PostgreSQL custom enum types (`CREATE TYPE ... AS ENUM`) cause Hibernate to fail when filtering with nullable parameters — PostgreSQL infers `null` as `bytea` or `character varying` and throws "operator does not exist" errors. Converting to `VARCHAR` fixes all JPQL `IS NULL OR col = :param` patterns across all repositories.

---

## API Endpoints

Base URL: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui.html`

### Authentication — `/api/v1/auth`

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/send-otp` | Send 6-digit OTP to phone | No |
| POST | `/verify-otp` | Verify OTP, receive access + refresh tokens | No |
| POST | `/refresh-token` | Exchange refresh token for new access token | No |
| POST | `/logout` | Invalidate refresh token | Yes |

### User Profile — `/api/v1/users`

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/me` | Get my profile | Yes |
| PUT | `/me` | Update my profile | Yes |
| POST | `/me/documents` | Upload driving licence or document | Yes |
| GET | `/me/documents` | List my uploaded documents | Yes |

### Owner — `/api/v1/owner`

#### Profile & KYC

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/me` | Get my owner KYC profile | Yes (Owner) |
| POST | `/me/complete-profile` | Submit business + bank details | Yes (Owner) |
| POST | `/me/documents` | Upload KYC document | Yes (Owner) |
| GET | `/me/documents` | List my KYC documents | Yes (Owner) |

#### Dashboard

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/dashboard` | Bike count, booking stats, total earnings | Yes (Owner) |

#### Bike Management

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/bikes` | List all my bike listings | Yes (Owner) |
| POST | `/bikes` | Add a new bike listing | Yes (Owner) |
| PUT | `/bikes/{id}` | Update a bike listing | Yes (Owner) |
| DELETE | `/bikes/{id}` | Delete a bike listing | Yes (Owner) |
| PATCH | `/bikes/{id}/availability` | Toggle AVAILABLE ↔ UNAVAILABLE | Yes (Owner) |

#### Bookings

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/bookings` | All bookings for my bikes | Yes (Owner) |

### Bikes (Public Browse) — `/api/v1/bikes`

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/` | Browse available bikes (filterable) | No |
| GET | `/{id}` | Get bike details | No |
| POST | `/` | Add a new bike listing | Yes (Owner) |
| PUT | `/{id}` | Update bike listing | Yes (Owner) |
| DELETE | `/{id}` | Delete bike listing | Yes (Owner) |
| GET | `/my-bikes` | List my bike listings | Yes (Owner) |
| PATCH | `/{id}/toggle-availability` | Toggle AVAILABLE ↔ UNAVAILABLE | Yes (Owner) |

> `/api/v1/bikes` owner endpoints are duplicated under `/api/v1/owner/bikes` to match the frontend API client.

### Bookings — `/api/v1/bookings`

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/estimate` | Price estimate before booking | No |
| POST | `/` | Create instant booking (auto-confirmed) | Yes (Customer) |
| GET | `/my` | Customer's booking history | Yes (Customer) |
| GET | `/{id}` | Get booking by numeric ID or reference string (e.g. PV-20240601-0001) | Yes |
| POST | `/{id}/pay` | Record payment (Phase 3: mocked) | Yes (Customer) |
| POST | `/{id}/start` | Mark ride started (CONFIRMED → RIDE_STARTED) | Yes (Customer) |
| POST | `/{id}/return-request` | Request bike return (RIDE_STARTED → RETURN_REQUESTED) | Yes (Customer) |
| PATCH | `/{id}/cancel` | Cancel booking | Yes (Customer) |
| POST | `/{id}/confirm` | Owner confirms booking (Phase 3: mocked) | Yes (Owner) |
| POST | `/{id}/complete` | Owner approves return (RETURN_REQUESTED → COMPLETED) | Yes (Owner) |
| PATCH | `/{id}/complete` | Admin force-completes booking | Yes (Admin) |
| GET | `/owner-bookings` | Bookings for owner's bikes (alias — prefer `/owner/bookings`) | Yes (Owner) |

### Admin — `/api/v1/admin`

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/dashboard` | Platform statistics | Yes (Admin) |
| GET | `/users` | List all users (paginated) | Yes (Admin) |
| PATCH | `/users/{userId}/suspend` | Suspend a user | Yes (Admin) |
| PATCH | `/users/{userId}/unsuspend` | Unsuspend a user | Yes (Admin) |
| GET | `/owners` | List owners by KYC status (paginated) | Yes (Admin) |
| PATCH | `/owners/{ownerId}/approve-kyc` | Approve owner KYC | Yes (Admin) |
| PATCH | `/owners/{ownerId}/reject-kyc` | Reject owner KYC with reason | Yes (Admin) |
| GET | `/bikes` | List bikes by verification status (paginated) | Yes (Admin) |
| PATCH | `/bikes/{bikeId}/approve` | Approve bike listing | Yes (Admin) |
| PATCH | `/bikes/{bikeId}/reject` | Reject bike listing with reason | Yes (Admin) |
| GET | `/bookings` | List all bookings (paginated) | Yes (Admin) |
| GET | `/commission` | Get current commission % | Yes (Admin) |
| PUT | `/commission` | Update commission % | Yes (Admin) |

---

## Local Setup

### Prerequisites
- Java 21+
- Maven 3.9+
- PostgreSQL 16 running on `localhost:5432`

### Option A — Docker Compose (recommended for staging/testing)

```bash
cp .env.example .env
# Edit .env: set DB_PASSWORD, and generate a JWT_SECRET with:
#   openssl rand -base64 48

docker compose up --build
```

This builds the app image, starts Postgres in a container, and runs Flyway
migrations (including seed data) automatically on a fresh volume. The app
starts on port `8080`; Postgres is also exposed on `5432` for inspection.

### Option B — Run directly with Maven (local dev)

#### 1. Create the database

```sql
CREATE DATABASE puduvandi;
```

#### 2. Configure credentials

Edit `src/main/resources/application.yml` if your PostgreSQL username/password differ from the defaults:

```yaml
spring:
  datasource:
    username: postgres
    password: postgres
```

#### 3. Run

```bash
mvn spring-boot:run
```

Flyway will auto-apply all migrations on startup. The app starts on port `8080`.

### Test the OTP flow (dev mode)

```bash
# Step 1 — request OTP (mock mode always sends 123456)
curl -X POST http://localhost:8080/api/v1/auth/send-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"9876543210","role":"CUSTOMER"}'

# Step 2 — verify OTP and receive tokens
curl -X POST http://localhost:8080/api/v1/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"9876543210","otp":"123456","role":"CUSTOMER"}'
```

Use the returned `accessToken` as a Bearer token for all authenticated endpoints.

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/puduvandi` | Postgres connection string |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` | Postgres credentials |
| `JWT_SECRET` | base64 dev placeholder | JWT signing secret — **must change per environment**. `SecurityStartupValidator` refuses to boot with the placeholder when `PUDUVANDI_ENV=production`. Generate with `openssl rand -base64 48`. |
| `PUDUVANDI_ENV` | unset | Set to `production` to enable the hard JWT-secret check above |
| `OTP_MOCK_ENABLED` | `true` | When true, OTP is always `OTP_MOCK_VALUE` — dev/staging only |
| `OTP_MOCK_VALUE` | `123456` | The mock OTP value |
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` / `TWILIO_PHONE_NUMBER` / `TWILIO_WHATSAPP_NUMBER` | blank | Twilio creds — SMS/WhatsApp notifications no-op (with a warning log) if blank |

See `.env.example` for the full list with comments.

---

## Build Log (What Was Done)

### Phase 1 — Auth
- `users`, `otp_records`, `refresh_tokens` tables (V1 migration)
- Phone OTP login with 5-minute expiry and mock-OTP dev mode
- JWT access token (15 min) + refresh token (7 days)
- `AuthController`, `AuthService`, `JwtUtil`, `JwtAuthenticationFilter`

### Phase 2 — User & Owner Profiles
- Customer profile: update name/email, upload documents
- Owner KYC: complete business + bank profile, upload KYC documents
- `owner_profiles`, `owner_documents`, `user_documents` tables (V2 migration)
- `UserController`, `OwnerController` with full CRUD DTOs

### Phase 3 — Bikes & Bookings
- `bikes`, `bike_images`, `bookings` tables (V3 migration)
- Owner: add/update/delete bike listings, toggle availability
- Customer: browse bikes with filters, instant booking (no owner approval)
- Full ride lifecycle: CREATED → CONFIRMED → RIDE_STARTED → RETURN_REQUESTED → COMPLETED
- Price estimate endpoint before committing to a booking

### Phase 3.1 — Bike Browse Bug Fixes
- Fixed: `LOWER(CONCAT('%', :brand, '%'))` in JPQL passed null as `bytea` → PostgreSQL `lower(bytea)` error. Fixed with `cast(:brand as string)` in JPQL (Hibernate 6 syntax).
- Fixed: All enum filter params (`fuelType`, `transmission`, `status`, etc.) failed with "operator does not exist: fuel_type = character varying" when null. Root cause: PostgreSQL custom enum types. V5 migration converts all enum columns to `VARCHAR(50)` permanently.

### Phase 4 — Admin Module
- `commission_settings` table seeded with 20% (V4 migration)
- Admin dashboard stats (total users, owners, bikes, bookings, revenue)
- User management: list, suspend, unsuspend
- Owner KYC review: approve / reject with reason
- Bike listing review: approve / reject with reason
- Booking management: paginated list with status filter
- Commission management: get and update platform %
- Fixed: `flyway-core` only in pom.xml (`flyway-database-postgresql` is Flyway 10+ only, incompatible with Spring Boot 3.2's Flyway 9)
- Fixed: `baseline-on-migrate: true` + `baseline-version: 3` added to application.yml

### Phase 4.1 — Frontend↔Backend API Alignment
- Added `/owner/bikes` GET/POST/PUT/DELETE/PATCH(availability) to `OwnerController` — frontend `ownerApi.js` calls these paths but backend only had them under `/bikes`
- Added `GET /owner/bookings` to `OwnerController`
- Added `GET /owner/dashboard` → returns totalBikes, totalBookings, activeBookings, totalEarnings
- Fixed `BookingController` paths to match frontend `bookingApi.js`:
  - `GET /bookings/my-bookings` → `GET /bookings/my`
  - `PATCH /{id}/start-ride` → `POST /{id}/start`
  - `PATCH /{id}/request-return` → `POST /{id}/return-request`
  - `GET /{reference}` (String) → now also accepts numeric ID (auto-detects)
  - Added `POST /{id}/pay` (payment mocked in Phase 3)
  - Added `POST /{id}/confirm` for Owner (mocked — booking already auto-confirms on create)
  - Added `POST /{id}/complete` for Owner (real — verifies ownership, completes booking)
- Fixed bug: `getOwnerBookings` was passing `userId` to a query that filters by `OwnerProfile.id` — now uses `findByOwner_UserIdAndDeletedFalse`
- Fixed bug: `getOwnerDashboard` called `findOrCreateOwnerProfile` inside `@Transactional(readOnly=true)` — caused INSERT on read-only transaction. Now reads profile optionally without creating

---

## Roadmap — What's Next

### Backend

| Module | Feature | Status |
|---|---|---|
| Auth/Security | OTP+JWT, refresh rotation, rate limiting, handover OTP for pickup/delivery/return | Done |
| Bikes/Bookings | Listings, instant booking, full ride lifecycle | Done |
| Owner/Admin | KYC, dashboards, commission + delivery-rate settings, error-log viewer | Done |
| Delivery/Partner | Partner KYC, job claiming, live location tracking | Done |
| Notifications | SMS/WhatsApp via Twilio on booking events | Done (needs a real, rotated Twilio account before go-live) |
| Docker | `Dockerfile` + `docker-compose.yml` (app + Postgres) | Done |
| Payment | Razorpay/Stripe integration, payment records table | **TODO — next milestone** |
| Earnings | Owner earnings ledger, payout summary endpoints | TODO |
| CI/CD | Automated test + build + deploy pipeline | TODO |

### Frontend (`E:/puduvandi-frontend` — React + Vite)

All phases below are done: Login/Auth, Customer + Owner + Partner profiles,
bike browse/detail, checkout (with a test-mode payment step pending real
Razorpay integration), bookings with OTP handover UI, owner dashboard +
bike management, partner dashboard, and a full admin console (users, owners,
partners, fleet, licences, settings, commission/delivery-rate, activity log).

Remaining frontend gaps mirror the backend: no real payment UI beyond the
test-mode step, no owner payout/earnings view beyond the dashboard total, and
the Capacitor Android/iOS scaffolds are unconfigured placeholders (`appId`
still `com.example.app`) — treat native app packaging as a separate project
if it's needed for launch.
