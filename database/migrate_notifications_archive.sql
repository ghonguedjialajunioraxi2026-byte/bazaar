-- MBOA STYLE — Archivage (sauvegarde) des notifications.
-- Ajoute is_archived (0 = boîte active, 1 = sauvegardée) + index.
-- Idempotent : réexécutable sans risque (colonne testée avant ajout).
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'is_archived'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE notifications ADD COLUMN is_archived TINYINT(1) NOT NULL DEFAULT 0, ADD INDEX idx_notif_archived (target_role, target_user_id, is_archived)',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
