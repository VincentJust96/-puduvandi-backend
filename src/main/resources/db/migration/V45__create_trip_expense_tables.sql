-- ============================================================
-- V45__create_trip_expense_tables.sql
-- TripSplit: multi-traveler trip expense tracking + splitting,
-- independent of the bike-rental role system (any authenticated
-- user can create/join trips regardless of CUSTOMER/OWNER/etc).
-- ============================================================

CREATE TABLE trips (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    budget      NUMERIC(12,2) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    created_by  VARCHAR(100),
    updated_by  VARCHAR(100)
);

-- One row per traveler on a trip. user_id is null until the invited phone
-- number matches (or later registers) a real account — see
-- TripExpenseService.linkPendingMemberships().
CREATE TABLE trip_members (
    id            BIGSERIAL PRIMARY KEY,
    trip_id       BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    user_id       BIGINT REFERENCES users(id),
    phone_number  VARCHAR(15) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    color_hex     VARCHAR(7) NOT NULL,
    is_creator    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP NOT NULL DEFAULT now(),
    created_by    VARCHAR(100),
    updated_by    VARCHAR(100),
    UNIQUE (trip_id, phone_number)
);

CREATE TABLE trip_expenses (
    id                 BIGSERIAL PRIMARY KEY,
    trip_id            BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    title              VARCHAR(200) NOT NULL,
    amount             NUMERIC(12,2) NOT NULL,
    category           VARCHAR(20) NOT NULL,
    paid_by_member_id  BIGINT NOT NULL REFERENCES trip_members(id),
    location           VARCHAR(100),
    expense_date       DATE NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP NOT NULL DEFAULT now(),
    created_by         VARCHAR(100),
    updated_by         VARCHAR(100)
);

-- Equal (or custom) split of one expense across the members it applies to.
CREATE TABLE trip_expense_splits (
    id            BIGSERIAL PRIMARY KEY,
    expense_id    BIGINT NOT NULL REFERENCES trip_expenses(id) ON DELETE CASCADE,
    member_id     BIGINT NOT NULL REFERENCES trip_members(id),
    share_amount  NUMERIC(12,2) NOT NULL,
    UNIQUE (expense_id, member_id)
);

-- A recorded "I paid you back" transfer between two members, created in bulk
-- by the Settle Up action. Offsets the balance calculation without being a
-- shared trip expense itself.
CREATE TABLE trip_settlement_payments (
    id              BIGSERIAL PRIMARY KEY,
    trip_id         BIGINT NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    from_member_id  BIGINT NOT NULL REFERENCES trip_members(id),
    to_member_id    BIGINT NOT NULL REFERENCES trip_members(id),
    amount          NUMERIC(12,2) NOT NULL,
    paid_at         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_trip_members_trip_id             ON trip_members(trip_id);
CREATE INDEX idx_trip_members_user_id             ON trip_members(user_id);
CREATE INDEX idx_trip_members_phone               ON trip_members(phone_number);
CREATE INDEX idx_trip_expenses_trip_id            ON trip_expenses(trip_id);
CREATE INDEX idx_trip_expenses_paid_by_member_id  ON trip_expenses(paid_by_member_id);
CREATE INDEX idx_trip_expense_splits_expense_id   ON trip_expense_splits(expense_id);
CREATE INDEX idx_trip_expense_splits_member_id    ON trip_expense_splits(member_id);
CREATE INDEX idx_trip_settlement_payments_trip_id ON trip_settlement_payments(trip_id);

CREATE TRIGGER trg_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_trip_members_updated_at
    BEFORE UPDATE ON trip_members
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_trip_expenses_updated_at
    BEFORE UPDATE ON trip_expenses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
