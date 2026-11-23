-- No backfill/reset: incompatible READY rows must be resolved explicitly before migration.
-- A single ALTER validates the new invariant without deleting or inventing budget data.
ALTER TABLE monthly_budgets
    DROP CHECK ck_food_enabled,
    DROP CHECK ck_food_on,
    DROP CHECK ck_budget_ready,
    DROP COLUMN food_enabled,
    ADD CONSTRAINT ck_budget_ready CHECK (setup_status <> 'READY' OR
        (food_weekday IS NOT NULL AND food_saturday IS NOT NULL AND food_sunday IS NOT NULL));
