-- Bank settlement details are being reworked into a separate payout flow;
-- owners no longer enter them as part of business-profile completion.
ALTER TABLE owner_profiles
    DROP COLUMN IF EXISTS bank_account_number,
    DROP COLUMN IF EXISTS bank_ifsc_code,
    DROP COLUMN IF EXISTS bank_name,
    DROP COLUMN IF EXISTS account_holder_name;
