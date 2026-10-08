-- =========================================================
-- MBOA STYLE — Migration PawaPay (collection MTN / Orange, XAF)
-- Idempotent : ré-exécutable sans erreur.
-- Montants : INT (FCFA, sans décimales — compatible PawaPay XAF).
-- Statuts PawaPay stockés tels quels (PENDING/PROCESSING/COMPLETED/
-- FAILED), jamais convertis arbitrairement en COMPLETED.
-- =========================================================

-- 1) Élargir payments.status aux statuts PawaPay (données conservées).
ALTER TABLE payments
  MODIFY COLUMN status ENUM(
    'EN_ATTENTE','REUSSIE','ECHOUEE','ANNULEE',
    'PENDING','PROCESSING','COMPLETED','FAILED','REFUNDED'
  ) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EN_ATTENTE';

-- 2) Devise : accepter XAF (historique FCFA conservé).
ALTER TABLE payments
  MODIFY COLUMN currency VARCHAR(10) COLLATE utf8mb4_unicode_ci DEFAULT 'XAF';

-- 3) Colonnes PawaPay (ajoutées seulement si absentes).
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'internal_reference');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN internal_reference VARCHAR(40) NULL AFTER idempotence_key, ADD UNIQUE KEY uq_pay_internal_ref (internal_reference)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'provider_transaction_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN provider_transaction_id VARCHAR(36) NULL AFTER internal_reference, ADD KEY idx_pay_provider_tx (provider_transaction_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'operator');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN operator VARCHAR(30) NULL AFTER provider_transaction_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'phone_number');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN phone_number VARCHAR(20) NULL AFTER operator',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'updated_at');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP AFTER paid_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) Journal d'idempotence des callbacks (anti double-traitement).
CREATE TABLE IF NOT EXISTS payment_events (
  id INT NOT NULL AUTO_INCREMENT,
  payment_id INT NOT NULL,
  event_key VARCHAR(80) NOT NULL COMMENT 'depositId:statut',
  payload JSON NULL,
  received_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_payment_event (event_key),
  KEY idx_pe_payment (payment_id),
  CONSTRAINT fk_pe_payment FOREIGN KEY (payment_id)
    REFERENCES payments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
