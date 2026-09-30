-- deleteTrip() relies entirely on DB-level ON DELETE CASCADE (Trip has no
-- JPA relationship mappings to cascade through in code). trips -> trip_members
-- and trips -> trip_expenses -> trip_expense_splits both cascade correctly,
-- but these three FKs into trip_members were left as plain (NO ACTION)
-- references — so cascading a trip delete hit a still-referenced member row
-- and the whole delete aborted. Trip deletion is documented (and intended)
-- to wipe everything under it, so these should cascade too. Individual
-- member removal (TripExpenseService.removeMember) already blocks removing
-- a member with expense history at the application level before this could
-- ever fire outside of a full trip delete.

ALTER TABLE trip_expenses DROP CONSTRAINT trip_expenses_paid_by_member_id_fkey;
ALTER TABLE trip_expenses ADD CONSTRAINT trip_expenses_paid_by_member_id_fkey
    FOREIGN KEY (paid_by_member_id) REFERENCES trip_members(id) ON DELETE CASCADE;

ALTER TABLE trip_expense_splits DROP CONSTRAINT trip_expense_splits_member_id_fkey;
ALTER TABLE trip_expense_splits ADD CONSTRAINT trip_expense_splits_member_id_fkey
    FOREIGN KEY (member_id) REFERENCES trip_members(id) ON DELETE CASCADE;

ALTER TABLE trip_settlement_payments DROP CONSTRAINT trip_settlement_payments_from_member_id_fkey;
ALTER TABLE trip_settlement_payments ADD CONSTRAINT trip_settlement_payments_from_member_id_fkey
    FOREIGN KEY (from_member_id) REFERENCES trip_members(id) ON DELETE CASCADE;

ALTER TABLE trip_settlement_payments DROP CONSTRAINT trip_settlement_payments_to_member_id_fkey;
ALTER TABLE trip_settlement_payments ADD CONSTRAINT trip_settlement_payments_to_member_id_fkey
    FOREIGN KEY (to_member_id) REFERENCES trip_members(id) ON DELETE CASCADE;
