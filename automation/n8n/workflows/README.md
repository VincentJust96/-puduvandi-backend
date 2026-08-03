# error-log-alert workflow

Alerts by email whenever a new `ERROR`-severity row lands in `error_logs`
(`WARN` rows — expected client errors — are filtered out by the SQL query,
matching how `GlobalExceptionHandler` already only auto-persists 404/500s).

## How it works

1. **Schedule Trigger** — fires every 1 minute. (An earlier version used n8n's
   "Postgres Trigger" node in LISTEN/NOTIFY mode, but that requires
   `CREATE TRIGGER`/`CREATE FUNCTION` privileges on the schema — too much
   access to grant a monitoring workflow. Plain polling only needs `SELECT`.)
2. **Execute a SQL query** (Postgres node) — pulls `ERROR`-severity rows from
   the last 5 minutes. The window is wider than the 1-minute poll interval on
   purpose, as a safety margin against any missed/delayed poll tick — the next
   node handles the resulting overlap.
3. **Code in JavaScript** — dedup step. Tracks the highest `error_logs.id`
   already alerted on on in the workflow's own static data (`$getWorkflowStaticData`),
   and filters out anything at or below it. This is what actually prevents
   duplicate emails when the same row shows up across multiple overlapping
   polls — the wide SQL window alone would otherwise double- or triple-send.
4. **Send alert email** — fires only for genuinely new rows.

## To import

n8n canvas -> "..." menu (top right) -> Import from File -> select
`error-log-alert.json`. Or open the file, copy all, click the canvas, Ctrl+V.

## Before it'll run

Two credentials referenced in the JSON as placeholders — n8n will show the
Postgres and Send Email nodes with a red "credential not set" marker until you
configure these from n8n's Credentials panel:

1. **Postgres** ("Puduvandi Neon DB") — host/port/db/user/password from your
   Neon connection string. A read-only Neon role is fine here (this workflow
   only ever runs `SELECT`).
2. **SMTP** ("Alert SMTP") — if using Gmail, generate an "app password" at
   myaccount.google.com/apppasswords (needs 2-Step Verification enabled
   first). **From Email must match the authenticated account** (or a verified
   alias) — Gmail rejects/silently drops mail claiming to be from a domain you
   don't own. Using the same address for both From and To is fine for this
   personal-alert use case.

Also update the "Send alert email" node's `toEmail`/`fromEmail` fields
(currently placeholders) to your real address.

## Known gotchas hit building this (see automation/n8n/ORACLE_VM_SETUP.md too)

- **Schedule Trigger interval type matters**: the node defaults to an "Hours"
  rule with a "Trigger at Minute" field — easy to leave as-is and think it
  means "every N minutes" when it actually means "once per hour, at minute
  N." Explicitly set the interval type to **Minutes**.
- **n8n's SQLite execution DB uses WAL mode** — if you ever inspect
  `database.sqlite` directly (e.g. via `docker cp`), copy the `-wal` and
  `-shm` sidecar files too, or recent executions won't show up.
- **"Published" is not the same as "actually scheduled"** in this n8n version
  — activating/publishing a workflow while n8n is already running doesn't
  always re-register its triggers. If nothing fires on its own after
  publishing, restart the n8n container to force a fresh scan of active
  workflows (check `docker compose logs n8n` for "Activated workflow ...").
- **Fan-out connections are easy to create by accident** in the visual
  editor (e.g. a node connects to two downstream nodes when only one was
  intended) — if a dedup/filter step doesn't seem to be taking effect, check
  for a direct bypass connection hiding behind an in-between node (drag nodes
  apart to expose overlapping lines).

## Testing without waiting on manual triggers

Since the deployed backend (Render free tier) auto-logs unexpected errors to
`error_logs`, a safe way to generate a real test row with no side effects is
sending a `GET` to a `POST`-only endpoint, e.g.:
```
curl https://puduvandi-backend.onrender.com/api/v1/auth/send-otp
```
This reliably produces a genuine `ERROR`-severity row via
`HttpRequestMethodNotSupportedException`. Note Render's free tier spins down
after 15 min idle, so the first request after a while can take 60-150s to
respond (cold start) — this is normal.
