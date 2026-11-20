-- One-time compatibility transition. All future inserts default to unverified.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET email_verified = TRUE;

CREATE TABLE email_verifications (
    email_key VARCHAR(254) NOT NULL,
    challenge_id VARCHAR(36) NULL,
    code_hash VARCHAR(255) NULL,
    proof_hash CHAR(64) NULL,
    expires_at DATETIME(6) NULL,
    proof_expires_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    window_started_at DATETIME(6) NULL,
    request_count INT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY(email_key),
    KEY ix_email_verifications_sent(sent_at),
    CONSTRAINT ck_email_verification_requests CHECK(request_count BETWEEN 0 AND 10),
    CONSTRAINT ck_email_verification_attempts CHECK(attempts BETWEEN 0 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
