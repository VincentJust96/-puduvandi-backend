-- ============================================================
-- V11__more_seed_data_and_real_bike_images.sql
-- 1. Replace placeholder bike images with REAL model-specific
--    photos (Wikimedia Commons — all URLs verified HTTP 200).
-- 2. Add more users, one more approved owner, 6 more bikes
--    (incl. one ELECTRIC), and 5 more bookings.
-- OTP for all users: 123456
-- ============================================================

-- ===================================================================
-- 1. REPLACE BIKE IMAGES WITH EXACT MODEL PHOTOS
-- ===================================================================

DELETE FROM bike_images;

INSERT INTO bike_images (bike_id, image_url, sort_order, created_at)
VALUES
  -- Royal Enfield Classic 350
  ((SELECT id FROM bikes WHERE registration_number = 'TN09AB1234'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/Royal%20Enfield%20Classic%20350.jpg?width=800', 0, NOW()),

  -- Honda Activa 6G
  ((SELECT id FROM bikes WHERE registration_number = 'TN09CD5678'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/Honda%20Activa%206G.jpg?width=800', 0, NOW()),

  -- TVS Apache RTR 200 4V (two angles)
  ((SELECT id FROM bikes WHERE registration_number = 'TN09EF9012'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/TVS%20Apache%20RTR%20200%204v.png?width=800', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09EF9012'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/TVS%20Apache%20RTR%20200%204V%20Front-Right%20Profile.jpg?width=800', 1, NOW()),

  -- Yamaha FZ 25 (FZ-S street twin — same FZ family)
  ((SELECT id FROM bikes WHERE registration_number = 'TN09GH3456'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/Yamaha%20fz%20s.JPG?width=800', 0, NOW()),

  -- Bajaj Pulsar NS 200 (two angles)
  ((SELECT id FROM bikes WHERE registration_number = 'TN09IJ7890'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/Bajaj%20Pulsar%20200%20NS.jpg?width=800', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09IJ7890'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/Bajaj%20Pulsar%20200%20ns%20grey%20and%20red.jpg?width=800', 1, NOW()),

  -- Honda CB Shine
  ((SELECT id FROM bikes WHERE registration_number = 'TN09KL2345'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/Honda%20Shine.JPG?width=800', 0, NOW()),

  -- TVS Jupiter 125
  ((SELECT id FROM bikes WHERE registration_number = 'TN04MN6789'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/TVS%20Jupiter%20Scooter.jpg?width=800', 0, NOW());


-- ===================================================================
-- 2. MORE USERS
-- ===================================================================

INSERT INTO users (phone_number, full_name, email, role, status, kyc_status, profile_image_url, is_deleted, created_at, updated_at)
VALUES
  -- Customers
  ('9000000105', 'Sanjay Verma',   'sanjay@example.com',  'CUSTOMER', 'ACTIVE',    'NOT_SUBMITTED', 'https://api.dicebear.com/7.x/initials/svg?seed=Sanjay', false, NOW() - INTERVAL '25 days', NOW()),
  ('9000000106', 'Anitha Ravi',    'anitha@example.com',  'CUSTOMER', 'ACTIVE',    'NOT_SUBMITTED', 'https://api.dicebear.com/7.x/initials/svg?seed=Anitha', false, NOW() - INTERVAL '35 days', NOW()),
  -- Soft-deleted customer (tests deleted filtering — must NOT appear in lists or log in)
  ('9000000107', 'Vikram Das',     'vikram@example.com',  'CUSTOMER', 'ACTIVE',    'NOT_SUBMITTED', NULL, true,  NOW() - INTERVAL '55 days', NOW()),
  -- New approved owner in Puducherry
  ('9000000204', 'Ravi Chandran',  'ravi@example.com',    'OWNER',    'ACTIVE',    'APPROVED',      'https://api.dicebear.com/7.x/initials/svg?seed=Ravi',   false, NOW() - INTERVAL '70 days', NOW());


-- ===================================================================
-- 3. OWNER PROFILE + DOCUMENTS — Ravi Chandran (Pondy Wheels)
-- ===================================================================

INSERT INTO owner_profiles (user_id, business_name, gstin, address_line1, address_line2, city, state, pincode,
                             bank_account_number, bank_ifsc_code, bank_name, account_holder_name, total_bikes, is_deleted, created_at, updated_at)
VALUES
  ((SELECT id FROM users WHERE phone_number = '9000000204'),
   'Pondy Wheels', '34AACPW5678B1Z2',
   '23 Beach Road', 'White Town',
   'Puducherry', 'Puducherry', '605001',
   '98765432109876', 'ICIC0002345', 'ICICI Bank', 'Ravi Chandran',
   6, false, NOW() - INTERVAL '70 days', NOW());

INSERT INTO owner_documents (owner_id, document_type, document_url, status, remarks, is_deleted, created_at, updated_at)
VALUES
  ((SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   'AADHAAR', 'https://picsum.photos/seed/aadhaar-ravi/400/280', 'APPROVED', 'Verified', false, NOW() - INTERVAL '68 days', NOW()),
  ((SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   'PAN', 'https://picsum.photos/seed/pan-ravi/400/280', 'APPROVED', 'Verified', false, NOW() - INTERVAL '68 days', NOW()),
  ((SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   'BANK_PASSBOOK', 'https://picsum.photos/seed/bank-ravi/400/280', 'APPROVED', 'Verified', false, NOW() - INTERVAL '67 days', NOW());


-- ===================================================================
-- 4. SIX MORE BIKES (Pondy Wheels) — incl. one ELECTRIC scooter
-- ===================================================================

INSERT INTO bikes (owner_id, brand, model, year, registration_number,
                   fuel_type, transmission, engine_capacity, helmet_included,
                   price_per_hour, price_per_day, security_deposit,
                   description, status, verification_status, is_deleted, created_at, updated_at)
VALUES
  -- Royal Enfield Himalayan — adventure tourer, AVAILABLE
  ((SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   'Royal Enfield', 'Himalayan 411', 2023, 'PY01AB1111',
   'PETROL', 'MANUAL', 411, true,
   200.00, 1500.00, 5000.00,
   'Adventure tourer built for every terrain. Perfect for Pondicherry to hill-station getaways. Luggage rack fitted.',
   'AVAILABLE', 'APPROVED', false, NOW() - INTERVAL '65 days', NOW()),

  -- Yamaha YZF-R15 — sport, AVAILABLE
  ((SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   'Yamaha', 'YZF-R15 V4', 2024, 'PY01CD2222',
   'PETROL', 'MANUAL', 155, true,
   160.00, 1200.00, 4500.00,
   'Race-inspired supersport with aggressive styling. Track DNA for the street.',
   'AVAILABLE', 'APPROVED', false, NOW() - INTERVAL '60 days', NOW()),

  -- KTM 200 Duke — naked sport, AVAILABLE
  ((SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   'KTM', '200 Duke', 2023, 'PY01EF3333',
   'PETROL', 'MANUAL', 199, true,
   170.00, 1250.00, 4500.00,
   'The corner rocket. Sharp handling, punchy engine, ready to race.',
   'AVAILABLE', 'APPROVED', false, NOW() - INTERVAL '58 days', NOW()),

  -- Ola S1 Pro — ELECTRIC scooter, AVAILABLE
  ((SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   'Ola', 'S1 Pro', 2024, 'PY01GH4444',
   'ELECTRIC', 'AUTOMATIC', NULL, false,
   80.00, 550.00, 2000.00,
   'Zero-emission electric scooter with 180km range. Silent, smooth, and eco-friendly beach cruising.',
   'AVAILABLE', 'APPROVED', false, NOW() - INTERVAL '50 days', NOW()),

  -- Honda Dio — city scooter, RESERVED (Anitha riding now)
  ((SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   'Honda', 'Dio', 2022, 'PY01IJ5555',
   'PETROL', 'AUTOMATIC', 110, false,
   55.00, 350.00, 1200.00,
   'Sporty compact scooter, super easy to ride. Best budget pick for city hops.',
   'RESERVED', 'APPROVED', false, NOW() - INTERVAL '45 days', NOW()),

  -- Suzuki Access 125 — RESERVED (Sanjay's confirmed booking)
  ((SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   'Suzuki', 'Access 125', 2023, 'PY01KL6666',
   'PETROL', 'AUTOMATIC', 124, false,
   60.00, 400.00, 1500.00,
   'Comfortable family scooter with big storage. Smooth 125cc engine.',
   'RESERVED', 'APPROVED', false, NOW() - INTERVAL '40 days', NOW());


-- ===================================================================
-- 5. REAL IMAGES FOR THE NEW BIKES
-- ===================================================================

INSERT INTO bike_images (bike_id, image_url, sort_order, created_at)
VALUES
  ((SELECT id FROM bikes WHERE registration_number = 'PY01AB1111'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/Royal%20Enfield%20Himalayan%20red%20black.jpg?width=800', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'PY01CD2222'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/2019%20Yamaha%20YZF-R15.jpg?width=800', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'PY01EF3333'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/KTM%20DUKE%20200%20front.JPG?width=800', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'PY01GH4444'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/OLA%20S1%20Pro%20Gen%201%20Electric%20Scooter.jpg?width=800', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'PY01IJ5555'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/Honda%20Dio%20Oct%202018.jpg?width=800', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'PY01KL6666'),
   'https://commons.wikimedia.org/wiki/Special:FilePath/Suzuki%20Access%20125%2C%202023.jpg?width=800', 0, NOW());


-- ===================================================================
-- 6. FIVE MORE BOOKINGS (commission 20%)
-- ===================================================================

INSERT INTO bookings (
  booking_reference, customer_id, bike_id, owner_id,
  pickup_datetime, return_datetime, actual_return_datetime,
  total_hours, total_days,
  base_amount, security_deposit, total_amount,
  commission_percent, commission_amount, owner_earning,
  helmet_included, status, cancellation_reason,
  is_deleted, created_at, updated_at
)
VALUES

  -- 7. Sanjay → Suzuki Access 125 — CONFIRMED (pickup in 2 days, 1 day)
  ('PV-20260704-0007',
   (SELECT id FROM users WHERE phone_number = '9000000105'),
   (SELECT id FROM bikes WHERE registration_number = 'PY01KL6666'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   NOW() + INTERVAL '2 days', NOW() + INTERVAL '3 days', NULL,
   24.00, 1.00,
   400.00, 1500.00, 1900.00,
   20.00, 80.00, 320.00,
   false, 'CONFIRMED', NULL,
   false, NOW() - INTERVAL '1 day', NOW()),

  -- 8. Anitha → Honda Dio — RIDE_STARTED (riding now)
  ('PV-20260702-0008',
   (SELECT id FROM users WHERE phone_number = '9000000106'),
   (SELECT id FROM bikes WHERE registration_number = 'PY01IJ5555'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   NOW() - INTERVAL '3 hours', NOW() + INTERVAL '21 hours', NULL,
   24.00, 1.00,
   350.00, 1200.00, 1550.00,
   20.00, 70.00, 280.00,
   false, 'RIDE_STARTED', NULL,
   false, NOW() - INTERVAL '4 hours', NOW()),

  -- 9. Anitha → RE Himalayan — COMPLETED (3-day trip, 10 days ago)
  ('PV-20260622-0009',
   (SELECT id FROM users WHERE phone_number = '9000000106'),
   (SELECT id FROM bikes WHERE registration_number = 'PY01AB1111'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   NOW() - INTERVAL '10 days', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days',
   72.00, 3.00,
   4500.00, 5000.00, 9500.00,
   20.00, 900.00, 3600.00,
   true, 'COMPLETED', NULL,
   false, NOW() - INTERVAL '11 days', NOW()),

  -- 10. Priya → Ola S1 Pro — COMPLETED (electric, 5 days ago)
  ('PV-20260627-0010',
   (SELECT id FROM users WHERE phone_number = '9000000102'),
   (SELECT id FROM bikes WHERE registration_number = 'PY01GH4444'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   NOW() - INTERVAL '5 days', NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days',
   24.00, 1.00,
   550.00, 2000.00, 2550.00,
   20.00, 110.00, 440.00,
   false, 'COMPLETED', NULL,
   false, NOW() - INTERVAL '6 days', NOW()),

  -- 11. Sanjay → KTM 200 Duke — CANCELLED
  ('PV-20260626-0011',
   (SELECT id FROM users WHERE phone_number = '9000000105'),
   (SELECT id FROM bikes WHERE registration_number = 'PY01EF3333'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Pondy Wheels'),
   NOW() - INTERVAL '6 days', NOW() - INTERVAL '5 days', NULL,
   24.00, 1.00,
   1250.00, 4500.00, 5750.00,
   20.00, 250.00, 1000.00,
   true, 'CANCELLED', 'Weather was bad, cancelled the trip',
   false, NOW() - INTERVAL '7 days', NOW());
