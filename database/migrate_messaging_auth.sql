-- =========================================================
-- BAZAAR — Messagerie : lecture datée + lien compte client.
-- - messages.read_at : date de lecture réelle (is_read conservé).
-- - conversations.customer_id : propriétaire authentifié (NULL = invité
--   phone+cid historique ; lié à la première utilisation authentifiée).
-- Idempotent : ré-exécutable sans erreur. Données préservées.
-- =========================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messages' AND COLUMN_NAME = 'read_at');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE messages ADD COLUMN read_at TIMESTAMP NULL DEFAULT NULL AFTER is_read',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill : lecture datée approximative pour l'historique déjà lu.
UPDATE messages SET read_at = created_at WHERE is_read = 1 AND read_at IS NULL;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversations' AND COLUMN_NAME = 'customer_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE conversations ADD COLUMN customer_id INT NULL AFTER supplier_id, ADD CONSTRAINT fk_conv_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE SET NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversations' AND INDEX_NAME = 'idx_conv_customer');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE conversations ADD KEY idx_conv_customer (customer_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
