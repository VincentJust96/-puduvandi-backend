-- ============================================================
-- V12__create_booking_reference_sequence.sql
-- Booking references were generated from an in-JVM AtomicLong that
-- reset to 1 on every restart, causing duplicate-key failures.
-- A DB sequence survives restarts and is safe across instances.
-- ============================================================

CREATE SEQUENCE booking_reference_seq START WITH 100 INCREMENT BY 1;
