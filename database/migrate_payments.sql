-- =========================================================
-- MBOA STYLE — Migration paiements Phase 11
-- Pas de PSP externe : COD + Mobile Money manuel vérifié.
-- Idempotent : ré-exécutable sans erreur.
-- =========================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'provider');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN provider VARCHAR(40) NULL AFTER method',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'idempotence_key');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN idempotence_key VARCHAR(80) NULL AFTER transaction_ref, ADD UNIQUE KEY uq_pay_idem (idempotence_key)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'failure_reason');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN failure_reason VARCHAR(255) NULL AFTER idempotence_key',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'verified_by');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN verified_by INT NULL AFTER failure_reason, ADD COLUMN verified_at TIMESTAMP NULL AFTER verified_by',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill provider depuis method pour l'historique
UPDATE payments SET provider = CASE
  WHEN method IN ('MOBILE_MONEY_MTN','MOBILE_MONEY_ORANGE') THEN 'MANUAL_MOMO'
  WHEN method = 'PAIEMENT_LIVRAISON' THEN 'COD'
  ELSE 'LEGACY' END
WHERE provider IS NULL;
