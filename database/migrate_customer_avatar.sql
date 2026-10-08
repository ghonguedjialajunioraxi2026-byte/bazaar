-- =========================================================
-- BAZAAR — Photo de profil client (facultative).
-- Sans photo, le compte reçoit le visuel réservé
-- (assets/img/default-client.svg, géré par le code).
-- Idempotent : ré-exécutable sans erreur.
-- Appliquer : mysql -u root -p mboa_style < database/migrate_customer_avatar.sql
-- =========================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'customers' AND COLUMN_NAME = 'avatar');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE customers ADD COLUMN avatar VARCHAR(500) NULL AFTER google_sub',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
