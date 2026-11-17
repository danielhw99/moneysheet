CREATE TABLE daily_balance_checks (
 id BIGINT NOT NULL AUTO_INCREMENT,
 user_id BIGINT NOT NULL,
 balance_date DATE NOT NULL,
 actual_balance BIGINT NULL,
 locked BIT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_balance_check_user_date (user_id, balance_date),
 CONSTRAINT fk_balance_check_user FOREIGN KEY (user_id) REFERENCES users(id),
 CONSTRAINT ck_balance_check_amount CHECK (actual_balance IS NULL OR actual_balance BETWEEN 0 AND 9999999999)
) ENGINE=InnoDB;
