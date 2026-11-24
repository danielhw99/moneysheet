-- Existing READY budgets keep their original full-month food allocation.
ALTER TABLE monthly_budgets ADD COLUMN food_start_date DATE NULL;
UPDATE monthly_budgets SET food_start_date=month_start WHERE setup_status='READY';
ALTER TABLE monthly_budgets
 ADD CONSTRAINT ck_food_start_month CHECK (food_start_date IS NULL OR (food_start_date>=month_start AND YEAR(food_start_date)=YEAR(month_start) AND MONTH(food_start_date)=MONTH(month_start))),
 ADD CONSTRAINT ck_food_start_ready CHECK (setup_status<>'READY' OR food_start_date IS NOT NULL);
