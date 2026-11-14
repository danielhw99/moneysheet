-- Preserve every item ID, amount, category and fixed-payment reference.
-- V1 is immutable. Deploy matching FE/BE together after explicit migration.
ALTER TABLE fixed_budget_items
  DROP CHECK ck_fixed_item_kind,
  DROP INDEX uq_allowance_month,
  DROP COLUMN allowance_month_id,
  DROP COLUMN kind,
  COMMENT = '고정비 항목. 전달 이름 카테고리 예산금액을 새 ID로 복사하며 월별 독립.';
