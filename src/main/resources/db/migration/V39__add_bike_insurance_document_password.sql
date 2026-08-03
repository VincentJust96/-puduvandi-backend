-- ============================================================
-- V39__add_bike_insurance_document_password.sql
-- The owner's insurance PDF may be password-protected — captured at upload
-- time (see InsuranceDocumentParser/FileController's insurance-details
-- endpoint) so admin/super-admin can open the document themselves.
-- Deliberately NOT exposed on the customer/owner-facing BikeResponse
-- mapping (BikeService.toResponse) — only AdminService's mapper returns it.
-- ============================================================

ALTER TABLE bikes ADD COLUMN insurance_document_password VARCHAR(255);
