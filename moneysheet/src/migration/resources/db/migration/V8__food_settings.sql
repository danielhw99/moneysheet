-- Preserve historical budgets; explicit selection is required for new drafts.
ALTER TABLE monthly_budgets
    ADD COLUMN food_enabled BOOLEAN NULL,
    ADD COLUMN food_payment_mode VARCHAR(20) NOT NULL DEFAULT 'THREE_INSTALLMENTS';
UPDATE monthly_budgets SET food_enabled=TRUE
    WHERE food_weekday IS NOT NULL AND food_saturday IS NOT NULL AND food_sunday IS NOT NULL;
ALTER TABLE monthly_budgets
    DROP CHECK ck_budget_ready,
    ADD CONSTRAINT ck_food_enabled CHECK (food_enabled IS NULL OR food_enabled IN (0,1)),
    ADD CONSTRAINT ck_food_mode CHECK (food_payment_mode IN ('THREE_INSTALLMENTS','MONTH_START')),
    ADD CONSTRAINT ck_food_complete CHECK (
        (food_weekday IS NULL AND food_saturday IS NULL AND food_sunday IS NULL) OR
        (food_weekday IS NOT NULL AND food_saturday IS NOT NULL AND food_sunday IS NOT NULL)),
    ADD CONSTRAINT ck_food_on CHECK (food_enabled IS NULL OR food_enabled=FALSE OR
        (food_weekday IS NOT NULL AND food_saturday IS NOT NULL AND food_sunday IS NOT NULL)),
    ADD CONSTRAINT ck_budget_ready CHECK (setup_status<>'READY' OR food_enabled IS NOT NULL);
