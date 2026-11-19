-- Preserve budget items, payments and transactions when a category is deleted.
-- Apply explicitly after backup; V1..V4 remain immutable.
ALTER TABLE fixed_budget_items
  DROP FOREIGN KEY fk_fixed_items_category,
  MODIFY COLUMN category_id BIGINT NULL,
  ADD CONSTRAINT fk_fixed_items_category_set_null FOREIGN KEY (category_id)
    REFERENCES categories(id) ON DELETE SET NULL ON UPDATE RESTRICT;

ALTER TABLE main_transactions
  DROP FOREIGN KEY fk_main_category,
  ADD CONSTRAINT fk_main_category_set_null FOREIGN KEY (category_id)
    REFERENCES categories(id) ON DELETE SET NULL ON UPDATE RESTRICT;
