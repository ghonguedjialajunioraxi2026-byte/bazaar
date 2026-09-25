-- =========================================================
-- CAMSHOP — Migration notifications v2 : priorité + index
-- Exécuter via : mysql -u root -p mboa_style < database/migrate_notifications_v2.sql
-- Idempotent : ré-exécutable sans erreur
-- =========================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'priority');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE notifications ADD COLUMN priority ENUM(''LOW'',''NORMAL'',''HIGH'',''URGENT'') NOT NULL DEFAULT ''NORMAL'' AFTER type',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND INDEX_NAME = 'idx_notif_role_read_date');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE notifications ADD INDEX idx_notif_role_read_date (target_role, is_read, created_at)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
