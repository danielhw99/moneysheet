CREATE TABLE remember_login_tokens (
 selector BINARY(16) NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL,
 validator_hash BINARY(32) NOT NULL,
 previous_validator_hash BINARY(32) NULL,
 previous_valid_until DATETIME(6) NULL,
 session_version BIGINT NOT NULL,
 issued_at DATETIME(6) NOT NULL,
 rotated_at DATETIME(6) NULL,
 expires_at DATETIME(6) NOT NULL,
 revoked_at DATETIME(6) NULL,
 KEY ix_remember_user (user_id),
 KEY ix_remember_expiry (expires_at),
 KEY ix_remember_revoked (revoked_at),
 CONSTRAINT fk_remember_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
 CONSTRAINT ck_remember_expiry CHECK (expires_at > issued_at),
 CONSTRAINT ck_remember_version CHECK (session_version >= 0),
 CONSTRAINT ck_remember_previous CHECK ((previous_validator_hash IS NULL AND previous_valid_until IS NULL) OR (previous_validator_hash IS NOT NULL AND previous_valid_until IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
