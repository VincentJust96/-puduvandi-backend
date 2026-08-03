# Udyam (MSME) registration — do this yourself

This is a personal legal filing tied to your Aadhaar — I can't do it for you (I
don't have your Aadhaar number, and the OTP goes to your Aadhaar-linked phone
only you can enter). It's free and takes about 10-15 minutes.

## Important: official site only

Use **udyamregistration.gov.in** — a `.gov.in` domain — only. There are many
lookalike third-party sites (`udyam-registration.com` and similar) that charge a
fee for something the government provides free. Don't enter your Aadhaar/PAN
anywhere except the real `.gov.in` site.

## Steps

1. Go to udyamregistration.gov.in -> "For New Entrepreneurs who are not
   Registered yet as MSME" (or "New Registration").
2. Enter your **Aadhaar number** and your name exactly as it appears on your
   Aadhaar card.
3. Click "Validate & Generate OTP" -> enter the OTP sent to your
   Aadhaar-linked mobile number.
4. Select organisation type — choose **"Proprietorship"** (matches Puduvandi
   as a sole-proprietor operation, no company registered).
5. Enter your **PAN** — the portal auto-validates it and pulls your ITR/GST
   linkage if any exists (fine if none does yet).
6. Fill business details: name (e.g. "Puduvandi"), address, bank account
   (IFSC + account number), main business activity (pick the closest NIC
   code — something like "renting of two-wheelers" / "transport rental
   services"), number of employees, investment/turnover (approximate is fine
   for a new/small operation).
7. Accept the self-declaration checkbox -> Submit -> a final OTP confirms
   the application.
8. You'll get a **Udyam Registration Number (URN)** immediately and a
   downloadable certificate (with QR code) — no renewal needed, doesn't
   expire.

## After you have it

Bring the Udyam certificate (and PAN) to MSG91 — that's the "firm
registration certificate" the DLT entity registration step needs for a sole
proprietor (see `automation/n8n/MSG91_SETUP.md`). Worth a quick confirmation
email to MSG91 (teamcrm@msg91.com) that Udyam is sufficient for your DLT PE
registration before you rely on it, since acceptance can vary slightly by
telecom operator.
