-- =========================================================
-- MBOA STYLE — Migration notifications v3 (Phase 13)
-- Exécuter via : mysql -u root -p mboa_style < database/migrate_notifications_v3.sql
-- Principe : ADDITIF uniquement. Aucune donnée historique supprimée.
-- Ajoute : event_code, category, entity_type/id, action_url,
--          dedup_key (idempotence), metadata, read_at,
--          préférences utilisateur, index perfs.
-- =========================================================

-- 1) Élargir le type (catégorie) : nouvelles valeurs réellement utilisées.
--    MySQL : redéfinir l'ENUM complet (anciennes valeurs conservées).
ALTER TABLE notifications
  MODIFY COLUMN `type` ENUM('ORDER','STOCK','SUPPLIER','PAYMENT','SYSTEM',
    'DELIVERY','MESSAGE','NEGOTIATION','SECURITY','PRODUCT','MARKETING')
  NOT NULL DEFAULT 'SYSTEM';

-- 2) Code événement précis (ex: ORDER_CREATED, PAYMENT_SUCCEEDED...).
SET @has_event := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'event_code');
SET @sql := IF(@has_event = 0,
  'ALTER TABLE notifications ADD COLUMN event_code VARCHAR(60) NULL AFTER `type`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) Catégorie fonctionnelle (préférences futures). Par défaut = type.
SET @has_cat := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'category');
SET @sql := IF(@has_cat = 0,
  'ALTER TABLE notifications ADD COLUMN category VARCHAR(30) NULL AFTER event_code',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) Entité liée (source de vérité : jamais une preuve d'autorisation).
SET @has_et := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'entity_type');
SET @sql := IF(@has_et = 0,
  'ALTER TABLE notifications ADD COLUMN entity_type VARCHAR(40) NULL AFTER category',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_eid := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'entity_id');
SET @sql := IF(@has_eid = 0,
  'ALTER TABLE notifications ADD COLUMN entity_id INT NULL AFTER entity_type',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5) URL d'action (relative uniquement, validée côté serveur).
SET @has_url := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'action_url');
SET @sql := IF(@has_url = 0,
  'ALTER TABLE notifications ADD COLUMN action_url VARCHAR(255) NULL AFTER entity_id',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6) Clé de déduplication (idempotence : 1 événement -> 1 notification).
SET @has_dedup := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'dedup_key');
SET @sql := IF(@has_dedup = 0,
  'ALTER TABLE notifications ADD COLUMN dedup_key VARCHAR(120) NULL AFTER action_url',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_meta := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'metadata');
SET @sql := IF(@has_meta = 0,
  'ALTER TABLE notifications ADD COLUMN metadata TEXT NULL AFTER dedup_key',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_readat := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND COLUMN_NAME = 'read_at');
SET @sql := IF(@has_readat = 0,
  'ALTER TABLE notifications ADD COLUMN read_at TIMESTAMP NULL DEFAULT NULL AFTER is_read',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7) Backfill : category = type quand NULL (données historiques préservées).
UPDATE notifications SET category = `type` WHERE category IS NULL;

-- 8) Unicité déduplication (NULL ignorés par MySQL : pas de conflit historique).
SET @has_udx := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND INDEX_NAME = 'uq_notif_dedup');
SET @sql := IF(@has_udx = 0,
  'ALTER TABLE notifications ADD UNIQUE INDEX uq_notif_dedup (dedup_key)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 9) Index perfs : compteur non lu + pagination + filtres.
SET @has_idx1 := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND INDEX_NAME = 'idx_notif_user_read_date');
SET @sql := IF(@has_idx1 = 0,
  'ALTER TABLE notifications ADD INDEX idx_notif_user_read_date (target_role, target_user_id, is_read, created_at)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx2 := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND INDEX_NAME = 'idx_notif_category');
SET @sql := IF(@has_idx2 = 0,
  'ALTER TABLE notifications ADD INDEX idx_notif_category (category)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx3 := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications' AND INDEX_NAME = 'idx_notif_entity');
SET @sql := IF(@has_idx3 = 0,
  'ALTER TABLE notifications ADD INDEX idx_notif_entity (entity_type, entity_id)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 10) Préférences par catégorie (opt-out marketing/informatif ; critiques forcées).
CREATE TABLE IF NOT EXISTS notification_preferences (
  id INT NOT NULL AUTO_INCREMENT,
  owner_role ENUM('ADMIN','SUPER_ADMIN','GESTIONNAIRE','SUPPLIER') NOT NULL,
  owner_user_id INT NULL COMMENT 'NULL = défaut du rôle',
  category VARCHAR(30) NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_notif_pref (owner_role, owner_user_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
