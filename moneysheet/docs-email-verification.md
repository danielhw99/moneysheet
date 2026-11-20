# Signup email verification (UR08)

Spring Boot Mail (`JavaMailSender`) sends through the configured SMTP server. Sender: `moneysheet.site@gmail.com`. Configure the variables in `.env.example` in the process/IDE environment; Spring does not load that file automatically. Never commit actual passwords or log mail bodies/codes.

- Gmail example: SMTP_HOST=smtp.gmail.com, SMTP_PORT=587, SMTP_USERNAME=moneysheet.site@gmail.com, SMTP_PASSWORD supplied privately. STARTTLS is required. Connection/read/write timeouts are five seconds each.
- Obtain GET /api/v1/auth/csrf, then POST /api/v1/auth/email-verifications with `{email}`. The response contains challengeId, expiresAt and resendAfterSeconds, never the code.
- POST /api/v1/auth/email-verifications/confirmation with `{email,challengeId,code}`. Response verificationToken expires after ten minutes and must be included in POST /api/v1/auth/signup. All POSTs require CSRF. New account authentication is not bypassed when SMTP is absent.
- Six digits, code lifetime ten minutes, resend after sixty seconds; resend invalidates prior code and proof. Five incorrect guesses exhaust the challenge. Email/send-IP limit ten per hour; confirmation-IP limit thirty per minute. Existing signup/login buckets remain independent. Process IP counters reset on restart; email counters and attempts are in MySQL.
- Existing users are marked verified once by V7. New database inserts default to false. Signup marks the account verified only after consuming the email-bound proof in the same transaction. Unverified users cannot log in. Legacy users are exempted by policy, not evidence of real mailbox ownership.
- Codes use salted Argon2 hashes; proofs are random256-bit values stored only as SHA256. Metadata older than24 hours is purged hourly; account deletion immediately deletes that email's row. Verification data is separate from financial data.
- Confirmation failures commit their attempt count. SMTP failures roll back challenge changes and return sanitized INTERNAL_ERROR/HTTP500. Unknown/expired/exhausted codes and invalid/consumed proofs use VALIDATION/400; existing email409; rate limit429 with Retry-After. No password recovery endpoint is introduced.

V7 is versioned SQL. Applying it to an existing DB requires the normal backup, schema/version check and explicit migration procedure. Application boot never migrates automatically. The PR does not apply V7 to the development database.

Validation uses a test-only in-memory mailbox; no real mail is sent by automated tests. Actual SMTP delivery, spam-folder behavior and sender authentication must be checked separately after private configuration. Fake delivery is never enabled in the application runtime.
