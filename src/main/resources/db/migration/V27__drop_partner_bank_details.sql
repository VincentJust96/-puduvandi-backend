-- Same reasoning as V24 (owner bank details): payout details are being
-- reworked into a separate flow, so delivery partners no longer enter them
-- as part of KYC profile completion either.
ALTER TABLE partner_profiles
    DROP COLUMN IF EXISTS bank_account_number,
    DROP COLUMN IF EXISTS bank_ifsc_code,
    DROP COLUMN IF EXISTS bank_name,
    DROP COLUMN IF EXISTS account_holder_name;
