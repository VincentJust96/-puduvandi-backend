-- ============================================================
-- V10__seed_dummy_data.sql
-- Comprehensive dummy data covering all app scenarios.
-- OTP for ALL users: 123456  (mock mode is enabled)
--
-- Personas:
--   ADMIN   : 9000000001 — Admin Puduvandi
--   CUSTOMER: 9000000101 — Arjun Kumar       (active, has bookings)
--             9000000102 — Priya Sharma       (active, confirmed booking)
--             9000000103 — Deepak Nair        (active, completed bookings)
--             9000000104 — Meena Lakshmi      (SUSPENDED — auth should fail)
--   OWNER   : 9000000201 — Muthu Raj         (KYC APPROVED, 5 bikes)
--             9000000202 — Selvi Devi         (KYC PENDING)
--             9000000203 — Karthik Balu       (KYC REJECTED)
-- ============================================================

-- ===================================================================
-- 1. USERS
-- ===================================================================

INSERT INTO users (phone_number, full_name, email, role, status, kyc_status, profile_image_url, is_deleted, created_at, updated_at)
VALUES
  -- Admin
  ('9000000001', 'Admin Puduvandi',  'admin@puduvandi.com',   'ADMIN',    'ACTIVE',               'APPROVED',      'https://api.dicebear.com/7.x/initials/svg?seed=Admin',   false, NOW() - INTERVAL '60 days', NOW()),
  -- Customers
  ('9000000101', 'Arjun Kumar',      'arjun@example.com',     'CUSTOMER', 'ACTIVE',               'NOT_SUBMITTED', 'https://api.dicebear.com/7.x/initials/svg?seed=Arjun',   false, NOW() - INTERVAL '45 days', NOW()),
  ('9000000102', 'Priya Sharma',     'priya@example.com',     'CUSTOMER', 'ACTIVE',               'NOT_SUBMITTED', 'https://api.dicebear.com/7.x/initials/svg?seed=Priya',   false, NOW() - INTERVAL '30 days', NOW()),
  ('9000000103', 'Deepak Nair',      'deepak@example.com',    'CUSTOMER', 'ACTIVE',               'NOT_SUBMITTED', 'https://api.dicebear.com/7.x/initials/svg?seed=Deepak',  false, NOW() - INTERVAL '20 days', NOW()),
  ('9000000104', 'Meena Lakshmi',    'meena@example.com',     'CUSTOMER', 'SUSPENDED',            'NOT_SUBMITTED', 'https://api.dicebear.com/7.x/initials/svg?seed=Meena',   false, NOW() - INTERVAL '10 days', NOW()),
  -- Owners
  ('9000000201', 'Muthu Raj',        'muthu@example.com',     'OWNER',    'ACTIVE',               'APPROVED',      'https://api.dicebear.com/7.x/initials/svg?seed=Muthu',   false, NOW() - INTERVAL '90 days', NOW()),
  ('9000000202', 'Selvi Devi',       'selvi@example.com',     'OWNER',    'ACTIVE',               'PENDING',       'https://api.dicebear.com/7.x/initials/svg?seed=Selvi',   false, NOW() - INTERVAL '15 days', NOW()),
  ('9000000203', 'Karthik Balu',     'karthik@example.com',   'OWNER',    'ACTIVE',               'REJECTED',      'https://api.dicebear.com/7.x/initials/svg?seed=Karthik', false, NOW() - INTERVAL '50 days', NOW());


-- ===================================================================
-- 2. OWNER PROFILES
-- ===================================================================

INSERT INTO owner_profiles (user_id, business_name, gstin, address_line1, address_line2, city, state, pincode,
                             bank_account_number, bank_ifsc_code, bank_name, account_holder_name, total_bikes, is_deleted, created_at, updated_at)
VALUES
  -- Muthu Raj — fully onboarded, 5 bikes
  ((SELECT id FROM users WHERE phone_number = '9000000201'),
   'Muthu Bikes Rental', '33AABCM1234A1Z5',
   '12 Anna Nagar West', '2nd Street, Block B',
   'Chennai', 'Tamil Nadu', '600040',
   '12345678901234', 'HDFC0001234', 'HDFC Bank', 'Muthu Raj',
   5, false, NOW() - INTERVAL '90 days', NOW()),

  -- Selvi Devi — KYC pending, no bikes live yet
  ((SELECT id FROM users WHERE phone_number = '9000000202'),
   'Selvi Rides', NULL,
   '5 Meenakshi Road', 'Near Temple',
   'Madurai', 'Tamil Nadu', '625001',
   NULL, NULL, NULL, NULL,
   0, false, NOW() - INTERVAL '15 days', NOW()),

  -- Karthik Balu — KYC rejected
  ((SELECT id FROM users WHERE phone_number = '9000000203'),
   'Karthik Motors', NULL,
   '8 Gandhipuram', 'Main Road',
   'Coimbatore', 'Tamil Nadu', '641012',
   NULL, NULL, NULL, NULL,
   0, false, NOW() - INTERVAL '50 days', NOW());


-- ===================================================================
-- 3. OWNER DOCUMENTS
-- ===================================================================

INSERT INTO owner_documents (owner_id, document_type, document_url, status, remarks, is_deleted, created_at, updated_at)
VALUES
  -- Muthu Raj — all approved
  ((SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   'AADHAAR', 'https://picsum.photos/seed/aadhaar-muthu/400/280', 'APPROVED', 'Verified', false, NOW() - INTERVAL '88 days', NOW()),
  ((SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   'PAN', 'https://picsum.photos/seed/pan-muthu/400/280', 'APPROVED', 'Verified', false, NOW() - INTERVAL '88 days', NOW()),
  ((SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   'BANK_PASSBOOK', 'https://picsum.photos/seed/bank-muthu/400/280', 'APPROVED', 'Verified', false, NOW() - INTERVAL '87 days', NOW()),

  -- Selvi Devi — pending review
  ((SELECT id FROM owner_profiles WHERE business_name = 'Selvi Rides'),
   'AADHAAR', 'https://picsum.photos/seed/aadhaar-selvi/400/280', 'PENDING', NULL, false, NOW() - INTERVAL '14 days', NOW()),
  ((SELECT id FROM owner_profiles WHERE business_name = 'Selvi Rides'),
   'PAN', 'https://picsum.photos/seed/pan-selvi/400/280', 'PENDING', NULL, false, NOW() - INTERVAL '14 days', NOW()),

  -- Karthik Balu — rejected with remarks
  ((SELECT id FROM owner_profiles WHERE business_name = 'Karthik Motors'),
   'AADHAAR', 'https://picsum.photos/seed/aadhaar-karthik/400/280', 'REJECTED', 'Document blurry, please re-upload', false, NOW() - INTERVAL '45 days', NOW());


-- ===================================================================
-- 4. BIKES  (all owned by Muthu Raj)
-- Statuses:
--   Bike 1 — APPROVED / RESERVED   (has CONFIRMED booking by Priya)
--   Bike 2 — APPROVED / RESERVED   (has RIDE_STARTED booking by Arjun)
--   Bike 3 — APPROVED / AVAILABLE  (has past COMPLETED and CANCELLED bookings)
--   Bike 4 — APPROVED / UNAVAILABLE (owner switched off)
--   Bike 5 — PENDING  / UNAVAILABLE (not yet admin-approved)
--   Bike 6 — PENDING  / UNAVAILABLE (Selvi's bike, KYC pending)
-- ===================================================================

INSERT INTO bikes (owner_id, brand, model, year, registration_number,
                   fuel_type, transmission, engine_capacity, helmet_included,
                   price_per_hour, price_per_day, security_deposit,
                   description, status, verification_status, is_deleted, created_at, updated_at)
VALUES
  -- Bike 1: Royal Enfield Classic 350 — RESERVED (Priya's confirmed booking)
  ((SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   'Royal Enfield', 'Classic 350', 2023, 'TN09AB1234',
   'PETROL', 'MANUAL', 349, true,
   120.00, 900.00, 3000.00,
   'Iconic classic cruiser — perfect for city rides and highway tours. Well maintained, helmet included.',
   'RESERVED', 'APPROVED', false, NOW() - INTERVAL '80 days', NOW()),

  -- Bike 2: Honda Activa 6G — RESERVED (Arjun's ride in progress)
  ((SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   'Honda', 'Activa 6G', 2022, 'TN09CD5678',
   'PETROL', 'AUTOMATIC', 110, false,
   60.00, 400.00, 1500.00,
   'Smooth city scooter, great fuel economy. Ideal for short errands and daily commute.',
   'RESERVED', 'APPROVED', false, NOW() - INTERVAL '75 days', NOW()),

  -- Bike 3: TVS Apache RTR 200 — AVAILABLE
  ((SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   'TVS', 'Apache RTR 200 4V', 2023, 'TN09EF9012',
   'PETROL', 'MANUAL', 197, true,
   150.00, 1100.00, 4000.00,
   'Performance sport bike with race-tuned suspension. ABS equipped, thrill guaranteed.',
   'AVAILABLE', 'APPROVED', false, NOW() - INTERVAL '70 days', NOW()),

  -- Bike 4: Yamaha FZ 25 — AVAILABLE (to browse and book)
  ((SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   'Yamaha', 'FZ 25', 2024, 'TN09GH3456',
   'PETROL', 'MANUAL', 249, false,
   130.00, 950.00, 3500.00,
   'Muscular street fighter with powerful engine. Comfortable for both city and long rides.',
   'AVAILABLE', 'APPROVED', false, NOW() - INTERVAL '60 days', NOW()),

  -- Bike 5: Bajaj Pulsar NS 200 — UNAVAILABLE (owner switched off)
  ((SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   'Bajaj', 'Pulsar NS 200', 2022, 'TN09IJ7890',
   'PETROL', 'MANUAL', 199, false,
   110.00, 800.00, 3000.00,
   'Sporty naked street bike. Currently under scheduled maintenance.',
   'UNAVAILABLE', 'APPROVED', false, NOW() - INTERVAL '55 days', NOW()),

  -- Bike 6: Honda CB Shine — PENDING verification (Muthu's new listing)
  ((SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   'Honda', 'CB Shine', 2024, 'TN09KL2345',
   'PETROL', 'MANUAL', 124, false,
   70.00, 500.00, 2000.00,
   'Reliable everyday commuter. Freshly serviced and ready to go.',
   'UNAVAILABLE', 'PENDING', false, NOW() - INTERVAL '5 days', NOW()),

  -- Bike 7: TVS Jupiter — PENDING (Selvi's listing, KYC pending)
  ((SELECT id FROM owner_profiles WHERE business_name = 'Selvi Rides'),
   'TVS', 'Jupiter 125', 2023, 'TN04MN6789',
   'PETROL', 'AUTOMATIC', 124, false,
   55.00, 380.00, 1200.00,
   'Premium family scooter with smooth ride. Very comfortable for city use.',
   'UNAVAILABLE', 'PENDING', false, NOW() - INTERVAL '10 days', NOW());


-- ===================================================================
-- 5. BIKE IMAGES  (3 images per bike — real photos via picsum.photos)
-- picsum.photos/seed/<name>/<w>/<h> always returns the same image.
-- ===================================================================

INSERT INTO bike_images (bike_id, image_url, sort_order, created_at)
VALUES
  -- Classic 350
  ((SELECT id FROM bikes WHERE registration_number = 'TN09AB1234'), 'https://picsum.photos/seed/re-classic-1/800/550', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09AB1234'), 'https://picsum.photos/seed/re-classic-2/800/550', 1, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09AB1234'), 'https://picsum.photos/seed/re-classic-3/800/550', 2, NOW()),

  -- Activa 6G
  ((SELECT id FROM bikes WHERE registration_number = 'TN09CD5678'), 'https://picsum.photos/seed/activa-1/800/550', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09CD5678'), 'https://picsum.photos/seed/activa-2/800/550', 1, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09CD5678'), 'https://picsum.photos/seed/activa-3/800/550', 2, NOW()),

  -- Apache RTR 200
  ((SELECT id FROM bikes WHERE registration_number = 'TN09EF9012'), 'https://picsum.photos/seed/apache-1/800/550', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09EF9012'), 'https://picsum.photos/seed/apache-2/800/550', 1, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09EF9012'), 'https://picsum.photos/seed/apache-3/800/550', 2, NOW()),

  -- Yamaha FZ 25
  ((SELECT id FROM bikes WHERE registration_number = 'TN09GH3456'), 'https://picsum.photos/seed/fz25-1/800/550', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09GH3456'), 'https://picsum.photos/seed/fz25-2/800/550', 1, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09GH3456'), 'https://picsum.photos/seed/fz25-3/800/550', 2, NOW()),

  -- Pulsar NS 200
  ((SELECT id FROM bikes WHERE registration_number = 'TN09IJ7890'), 'https://picsum.photos/seed/pulsar-1/800/550', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09IJ7890'), 'https://picsum.photos/seed/pulsar-2/800/550', 1, NOW()),

  -- CB Shine
  ((SELECT id FROM bikes WHERE registration_number = 'TN09KL2345'), 'https://picsum.photos/seed/cbshine-1/800/550', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN09KL2345'), 'https://picsum.photos/seed/cbshine-2/800/550', 1, NOW()),

  -- TVS Jupiter
  ((SELECT id FROM bikes WHERE registration_number = 'TN04MN6789'), 'https://picsum.photos/seed/jupiter-1/800/550', 0, NOW()),
  ((SELECT id FROM bikes WHERE registration_number = 'TN04MN6789'), 'https://picsum.photos/seed/jupiter-2/800/550', 1, NOW());


-- ===================================================================
-- 6. USER DOCUMENTS  (Arjun has uploaded his driving licence)
-- ===================================================================

INSERT INTO user_documents (user_id, document_type, document_url, status, remarks, is_deleted, created_at, updated_at)
VALUES
  ((SELECT id FROM users WHERE phone_number = '9000000101'),
   'DRIVING_LICENSE', 'https://picsum.photos/seed/dl-arjun/400/280', 'APPROVED', 'Verified', false, NOW() - INTERVAL '40 days', NOW()),
  ((SELECT id FROM users WHERE phone_number = '9000000103'),
   'DRIVING_LICENSE', 'https://picsum.photos/seed/dl-deepak/400/280', 'PENDING', NULL, false, NOW() - INTERVAL '18 days', NOW());


-- ===================================================================
-- 7. BOOKINGS
--
-- Booking 1  — Priya    → Classic 350    → CONFIRMED    (upcoming ride, bike=RESERVED)
-- Booking 2  — Arjun    → Activa 6G      → RIDE_STARTED (in progress,   bike=RESERVED)
-- Booking 3  — Deepak   → Apache RTR 200 → RETURN_REQUESTED (near end)
-- Booking 4  — Arjun    → Apache RTR 200 → COMPLETED    (past)
-- Booking 5  — Deepak   → Classic 350    → COMPLETED    (past)
-- Booking 6  — Arjun    → Yamaha FZ 25   → CANCELLED
-- ===================================================================

-- ---- Booking helpers (amounts pre-calculated) ----
-- Classic 350 : ₹900/day | ₹3000 deposit
-- Activa 6G   : ₹400/day | ₹1500 deposit
-- Apache RTR  : ₹1100/day| ₹4000 deposit
-- Yamaha FZ   : ₹950/day | ₹3500 deposit
-- Commission  : 20%

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

  -- 1. Priya → Classic 350 — CONFIRMED (pickup tomorrow, 2 days)
  ('PV-20260703-0001',
   (SELECT id FROM users WHERE phone_number = '9000000102'),
   (SELECT id FROM bikes WHERE registration_number = 'TN09AB1234'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   NOW() + INTERVAL '1 day', NOW() + INTERVAL '3 days', NULL,
   48.00, 2.00,
   1800.00, 3000.00, 4800.00,
   20.00, 360.00, 1440.00,
   true, 'CONFIRMED', NULL,
   false, NOW() - INTERVAL '2 days', NOW()),

  -- 2. Arjun → Activa 6G — RIDE_STARTED (picked up this morning)
  ('PV-20260702-0002',
   (SELECT id FROM users WHERE phone_number = '9000000101'),
   (SELECT id FROM bikes WHERE registration_number = 'TN09CD5678'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   NOW() - INTERVAL '6 hours', NOW() + INTERVAL '18 hours', NULL,
   24.00, 1.00,
   400.00, 1500.00, 1900.00,
   20.00, 80.00, 320.00,
   false, 'RIDE_STARTED', NULL,
   false, NOW() - INTERVAL '7 hours', NOW()),

  -- 3. Deepak → Apache RTR 200 — RETURN_REQUESTED
  ('PV-20260702-0003',
   (SELECT id FROM users WHERE phone_number = '9000000103'),
   (SELECT id FROM bikes WHERE registration_number = 'TN09EF9012'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 hour', NULL,
   47.00, 1.96,
   1100.00, 4000.00, 5100.00,
   20.00, 220.00, 880.00,
   true, 'RETURN_REQUESTED', NULL,
   false, NOW() - INTERVAL '2 days', NOW()),

  -- 4. Arjun → Apache RTR 200 — COMPLETED (last week)
  ('PV-20260625-0004',
   (SELECT id FROM users WHERE phone_number = '9000000101'),
   (SELECT id FROM bikes WHERE registration_number = 'TN09EF9012'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   NOW() - INTERVAL '8 days', NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days',
   24.00, 1.00,
   1100.00, 4000.00, 5100.00,
   20.00, 220.00, 880.00,
   true, 'COMPLETED', NULL,
   false, NOW() - INTERVAL '9 days', NOW()),

  -- 5. Deepak → Classic 350 — COMPLETED (two weeks ago)
  ('PV-20260618-0005',
   (SELECT id FROM users WHERE phone_number = '9000000103'),
   (SELECT id FROM bikes WHERE registration_number = 'TN09AB1234'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   NOW() - INTERVAL '15 days', NOW() - INTERVAL '13 days', NOW() - INTERVAL '13 days',
   48.00, 2.00,
   1800.00, 3000.00, 4800.00,
   20.00, 360.00, 1440.00,
   true, 'COMPLETED', NULL,
   false, NOW() - INTERVAL '16 days', NOW()),

  -- 6. Arjun → Yamaha FZ 25 — CANCELLED
  ('PV-20260620-0006',
   (SELECT id FROM users WHERE phone_number = '9000000101'),
   (SELECT id FROM bikes WHERE registration_number = 'TN09GH3456'),
   (SELECT id FROM owner_profiles WHERE business_name = 'Muthu Bikes Rental'),
   NOW() - INTERVAL '10 days', NOW() - INTERVAL '9 days', NULL,
   24.00, 1.00,
   950.00, 3500.00, 4450.00,
   20.00, 190.00, 760.00,
   false, 'CANCELLED', 'Change of plans, will rebook later',
   false, NOW() - INTERVAL '12 days', NOW());


-- ===================================================================
-- Fix bike status to match active bookings
-- (Booking 3 is RETURN_REQUESTED — Apache is still "in use", keep RESERVED)
-- ===================================================================

UPDATE bikes SET status = 'RESERVED'
WHERE registration_number IN ('TN09AB1234', 'TN09CD5678', 'TN09EF9012');
