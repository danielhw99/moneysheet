-- Initial MoneySheet schema, based on the approved ERD and data policies.
-- Cross-table ownership, record month, setup state and payment totals are validated by BE.
-- No seed data or database creation. Apply explicitly with dbMigrate after backup.

CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  email VARCHAR(254) NOT NULL,
  email_key VARCHAR(254) NOT NULL,
  nickname VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  privacy_policy_version VARCHAR(50) NOT NULL,
  privacy_agreed_at DATETIME(6) NOT NULL,
  UNIQUE KEY uq_users_email (email_key),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE categories (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  scope VARCHAR(30) NOT NULL,
  direction VARCHAR(10) NOT NULL,
  name VARCHAR(100) NOT NULL,
  name_key VARCHAR(100) NOT NULL,
  UNIQUE KEY uq_categories_name (user_id, scope, direction, name_key),
  CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_categories_scope CHECK (
    (scope = 'FIXED_BUDGET' AND direction = 'NONE') OR
    (scope = 'MAIN_TRANSACTION' AND direction IN ('INCOME', 'EXPENSE'))
  ),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE monthly_budgets (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  month_start DATE NOT NULL,
  setup_status VARCHAR(10) NOT NULL,
  food_weekday BIGINT NULL,
  food_saturday BIGINT NULL,
  food_sunday BIGINT NULL,
  UNIQUE KEY uq_monthly_budgets_month (user_id, month_start),
  CONSTRAINT fk_monthly_budgets_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_budget_month CHECK (DAYOFMONTH(month_start) = 1),
  CONSTRAINT ck_budget_status CHECK (setup_status IN ('DRAFT', 'READY')),
  CONSTRAINT ck_food_values CHECK (
    (food_weekday IS NULL OR food_weekday BETWEEN 1 AND 9999999999) AND
    (food_saturday IS NULL OR food_saturday BETWEEN 1 AND 9999999999) AND
    (food_sunday IS NULL OR food_sunday BETWEEN 1 AND 9999999999)),
  CONSTRAINT ck_budget_ready CHECK (setup_status <> 'READY' OR
    (food_weekday IS NOT NULL AND food_saturday IS NOT NULL AND food_sunday IS NOT NULL)),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='현재 월만 생성. 전달 고정비 예산 리스트를 기본값 복사 후 확인 저장. 식비도 전달 값 복사.';

CREATE TABLE fixed_budget_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  monthly_budget_id BIGINT NOT NULL,
  category_id BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  name VARCHAR(100) NOT NULL,
  amount BIGINT NOT NULL,
  allowance_month_id BIGINT GENERATED ALWAYS AS
    (CASE WHEN kind = 'FREE_ALLOWANCE' THEN monthly_budget_id ELSE NULL END) STORED,
  UNIQUE KEY uq_allowance_month (allowance_month_id),
  KEY ix_fixed_items_budget (monthly_budget_id),
  KEY ix_fixed_items_category (category_id),
  CONSTRAINT fk_fixed_items_budget FOREIGN KEY (monthly_budget_id) REFERENCES monthly_budgets(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_fixed_items_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_fixed_item_kind CHECK (kind IN ('FIXED_EXPENSE', 'FREE_ALLOWANCE')),
  CONSTRAINT ck_fixed_item_amount CHECK (amount BETWEEN 1 AND 9999999999),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='전달 항목 이름 종류 카테고리 예산금액을 새 ID로 복사. 복사 후 월별 독립.';

CREATE TABLE main_transactions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  monthly_budget_id BIGINT NOT NULL,
  occurred_on DATE NOT NULL,
  occurred_time TIME NOT NULL,
  title VARCHAR(255) NOT NULL,
  amount BIGINT NOT NULL,
  direction VARCHAR(10) NOT NULL,
  category_id BIGINT NULL,
  CONSTRAINT fk_main_category FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_main_direction CHECK (direction IN ('INCOME', 'EXPENSE')),
  CONSTRAINT fk_main_transactions_parent FOREIGN KEY (monthly_budget_id) REFERENCES monthly_budgets(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_main_transactions_amount CHECK (amount BETWEEN 1 AND 9999999999),
  CONSTRAINT ck_main_transactions_time CHECK (occurred_time >= '00:00:00' AND occurred_time <= '23:59:00' AND SECOND(occurred_time) = 0),
  KEY ix_main_transactions_date (monthly_budget_id, occurred_on, occurred_time, id),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE money_memos (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  monthly_budget_id BIGINT NOT NULL,
  occurred_on DATE NOT NULL,
  occurred_time TIME NOT NULL,
  title VARCHAR(255) NOT NULL,
  amount BIGINT NOT NULL,
  direction VARCHAR(10) NOT NULL,
  completed_on DATE NULL,
  CONSTRAINT ck_memo_direction CHECK (direction IN ('INCOME', 'EXPENSE')),
  CONSTRAINT ck_memo_completion CHECK (completed_on IS NULL OR
    (completed_on >= occurred_on AND EXTRACT(YEAR_MONTH FROM completed_on) = EXTRACT(YEAR_MONTH FROM occurred_on))),
  CONSTRAINT fk_money_memos_parent FOREIGN KEY (monthly_budget_id) REFERENCES monthly_budgets(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_money_memos_amount CHECK (amount BETWEEN 1 AND 9999999999),
  CONSTRAINT ck_money_memos_time CHECK (occurred_time >= '00:00:00' AND occurred_time <= '23:59:00' AND SECOND(occurred_time) = 0),
  KEY ix_money_memos_date (monthly_budget_id, occurred_on, occurred_time, id),
  KEY ix_memo_completed (monthly_budget_id, completed_on),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE fixed_payments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  fixed_budget_item_id BIGINT NOT NULL,
  occurred_on DATE NOT NULL,
  occurred_time TIME NOT NULL,
  title VARCHAR(255) NOT NULL,
  amount BIGINT NOT NULL,
  CONSTRAINT fk_fixed_payments_parent FOREIGN KEY (fixed_budget_item_id) REFERENCES fixed_budget_items(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_fixed_payments_amount CHECK (amount BETWEEN 1 AND 9999999999),
  CONSTRAINT ck_fixed_payments_time CHECK (occurred_time >= '00:00:00' AND occurred_time <= '23:59:00' AND SECOND(occurred_time) = 0),
  KEY ix_fixed_payments_date (fixed_budget_item_id, occurred_on, occurred_time, id),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin COMMENT='실제 지급 기록. 전달 기록 복사 금지. 지급합계와 잔여는 계산.';

CREATE TABLE monthly_cash_flows (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  monthly_budget_id BIGINT NOT NULL,
  occurred_on DATE NOT NULL,
  occurred_time TIME NOT NULL,
  title VARCHAR(255) NOT NULL,
  amount BIGINT NOT NULL,
  kind VARCHAR(20) NOT NULL,
  CONSTRAINT ck_cash_flow_kind CHECK (kind IN ('INCOME', 'EXTRA_EXPENSE')),
  CONSTRAINT fk_monthly_cash_flows_parent FOREIGN KEY (monthly_budget_id) REFERENCES monthly_budgets(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_monthly_cash_flows_amount CHECK (amount BETWEEN 1 AND 9999999999),
  CONSTRAINT ck_monthly_cash_flows_time CHECK (occurred_time >= '00:00:00' AND occurred_time <= '23:59:00' AND SECOND(occurred_time) = 0),
  KEY ix_monthly_cash_flows_date (monthly_budget_id, occurred_on, occurred_time, id),
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
