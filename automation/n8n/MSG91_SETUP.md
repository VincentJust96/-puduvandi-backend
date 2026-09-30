# MSG91 signup + DLT registration

This part is entirely on MSG91's dashboard + India's DLT portal — I can't do it for
you (needs your business identity documents), but here's the exact path and what to
send back to me once it's done.

## 1. Sign up

- Create an account at msg91.com. Free signup, no cost until you actually send SMS
  (pay-as-you-go per SMS after DLT is approved).

## 2. DLT registration (mandatory for sending to Indian +91 numbers — TRAI requirement,
   applies no matter which SMS provider you use)

DLT registration is a 3-stage chain, all done from MSG91's dashboard under **SMS ->
DLT** (they walk you through submitting to the actual DLT operator behind the
scenes):

1. **Principal Entity (PE) registration** — register yourself/your business as the
   sender. You'll need:
   - PAN card copy
   - GST registration certificate OR Certificate of Incorporation (proof of
     business address/legal status)
   - A Letter of Authorization (LOA) if someone other than the entity owner is
     handling registration — MSG91 gives you a template for this.
   - Puduvandi has no GST/incorporation yet, so a personal PAN alone isn't
     enough — TRAI requires at least sole-proprietor-level registration. See
     `automation/n8n/UDYAM_REGISTRATION.md` for the free, ~10-15 min online
     path (Udyam/MSME registration) that covers this.
2. **Header (Sender ID) registration** — register the 6-character sender ID that
   will show as the SMS "from" (e.g. `PDVNDI`). Tied to your approved PE.
3. **Content Template registration** — register the *exact* wording of each message
   you'll send, with variable placeholders. You need one template per message type
   we currently have:
   - Booking confirmation (`BookingConfirmationService` — confirmation template)
   - Pickup reminder
   - Ride/return completion
   - (OTP has its own template if you turn off `OTP_MOCK_ENABLED` later)
   MSG91 requires the template content to match what you'll actually send,
   character-for-character except for the `{#var#}` placeholders — so lock down the
   final wording of these 3 messages before submitting templates, since edits after
   approval mean re-submitting.

Typical turnaround: PE registration 1-2 business days, header + templates a few
hours to a day each once PE is approved.

## 3. What to hand back to me once approved

From the MSG91 dashboard you'll have:
- **Auth Key** (Settings -> API -> Auth Key)
- **Sender ID** (the approved header)
- **Template/Flow ID** for each of the 3 message types

Once you have those, I'll wire them into `application.yml`/`.env` and replace the
stub in `NotificationService.attemptSend()` with the real MSG91 call. MSG91's
dashboard also shows ready-made sample code (Settings -> API -> your flow -> "Sample
Code") for your *specific* approved template — I'll match the request body to that
exactly rather than guessing generic field names, since the Flow API's JSON shape
is account/template-specific (variable names must match what you registered
verbatim).

## In the meantime

I've added the config plumbing and an `Msg91Client` integration point in the code
now (see `NotificationService`), so the moment you hand me the authkey/sender/
template IDs, wiring it in is a small, contained change — nothing else in the
booking/handover flow needs to touch.
