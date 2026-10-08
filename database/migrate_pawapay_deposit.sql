-- =========================================================
-- MBOA STYLE — Migration PawaPay Deposit (complément).
-- Idempotent : ré-exécutable sans erreur. Appliquer APRÈS
-- migrate_pawapay.sql.
-- client_reference_id = order_number envoyé à PawaPay
-- (champ clientReferenceId), pour réconciliation dashboard.
-- =========================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'client_reference_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN client_reference_id VARCHAR(30) NULL AFTER provider_transaction_id, ADD KEY idx_pay_client_ref (client_reference_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill : client_reference_id depuis la commande liée.
UPDATE payments p
  JOIN orders o ON o.id = p.order_id
  SET p.client_reference_id = o.order_number
  WHERE p.provider = 'PAWAPAY' AND p.client_reference_id IS NULL;
