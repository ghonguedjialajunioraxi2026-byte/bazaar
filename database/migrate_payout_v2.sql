-- =========================================================
-- BAZAAR — Migration Payout v2 (6 capacités PawaPay).
-- Idempotent : ré-exécutable sans erreur. Appliquer APRÈS
-- migrate_payout_refund.sql. Données existantes préservées.
--
--  1) supplier_payments.status += ACCEPTED, ENQUEUED (statuts distants
--     PawaPay stockés tels quels, jamais convertis arbitrairement).
--  2) provider_transaction_id : référence assignée PAR le provider
--     (visible sur le reçu SMS du destinataire, transmise en callback).
--  3) last_reconciled_at : date du dernier polling/callback (supervision).
--  4) refunds : mêmes colonnes de traçabilité (sans nouveau statut :
--     ENQUEUED reste mappé PROCESSING côté refund, pas de cancel refund
--     dans le périmètre).
-- =========================================================

-- 1) Statuts distants PawaPay persistés tels quels.
ALTER TABLE supplier_payments
  MODIFY COLUMN status ENUM(
    'PENDING','PROCESSING','PAID','FAILED',
    'COMPLETED','CANCELLED','BLOCKED',
    'ACCEPTED','ENQUEUED'
  ) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING';

-- 2) Référence provider (reçu SMS destinataire) + horodatage réconciliation.
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'provider_transaction_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN provider_transaction_id VARCHAR(60) NULL AFTER payout_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'last_reconciled_at');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN last_reconciled_at TIMESTAMP NULL DEFAULT NULL AFTER failure_reason',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) Refunds : mêmes colonnes de traçabilité.
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND COLUMN_NAME = 'provider_transaction_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE refunds ADD COLUMN provider_transaction_id VARCHAR(60) NULL AFTER deposit_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND COLUMN_NAME = 'last_reconciled_at');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE refunds ADD COLUMN last_reconciled_at TIMESTAMP NULL DEFAULT NULL AFTER failure_reason',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
