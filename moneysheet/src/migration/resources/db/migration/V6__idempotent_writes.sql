-- Metadata only: never retain original financial input or response snapshots.
CREATE TABLE write_requests (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  request_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  month_start DATE NOT NULL,
  request_hash VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  resource_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_write_requests_owner_key (user_id, request_key),
  CONSTRAINT fk_write_requests_owner FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT ck_write_requests_operation CHECK (operation IN ('transactions','memos','fixed-payments','cash-flows','budget-items')),
  CONSTRAINT ck_write_requests_month CHECK (DAYOFMONTH(month_start) = 1),
  CONSTRAINT ck_write_requests_resource CHECK (resource_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
