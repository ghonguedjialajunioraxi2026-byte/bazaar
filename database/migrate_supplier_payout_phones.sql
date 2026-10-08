-- =========================================================
-- BAZAAR — Numéros de versement par opérateur (MTN + Orange).
-- Règle : payout vers le numéro du MÊME opérateur que le dépôt client
-- (MTN payé → numéro MTN, Orange payé → numéro Orange).
-- Les anciennes colonnes payout_provider/payout_phone sont conservées
-- (historique) mais plus utilisées. Idempotent.
-- =========================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suppliers' AND COLUMN_NAME = 'mtn_phone');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE suppliers ADD COLUMN mtn_phone VARCHAR(20) NULL AFTER payout_phone',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suppliers' AND COLUMN_NAME = 'orange_phone');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE suppliers ADD COLUMN orange_phone VARCHAR(20) NULL AFTER mtn_phone',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
