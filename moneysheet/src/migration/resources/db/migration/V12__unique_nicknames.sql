-- LOWER is evaluated by MySQL in both the generated key and repository lookup.
-- Binary comparison preserves accents, internal spaces and full-width characters.
-- Existing duplicate nicknames cause this single atomic ALTER to fail unchanged.
-- Never rename or delete existing users automatically to resolve a collision.
ALTER TABLE users
  ADD COLUMN nickname_key VARCHAR(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin
    GENERATED ALWAYS AS (LOWER(nickname)) STORED,
  ADD UNIQUE KEY uq_users_nickname_key (nickname_key);
