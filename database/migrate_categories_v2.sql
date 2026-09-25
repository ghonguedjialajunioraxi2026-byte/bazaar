-- =========================================================
-- CAMSHOP — Migration catégories v2 : hiérarchie illimitée
-- Exécuter via : mysql -u root -p mboa_style < database/migrate_categories_v2.sql
-- Idempotent : ré-exécutable sans erreur (colonnes ajoutées si absentes)
-- =========================================================

-- Colonne parent (hiérarchie illimitée : NULL = catégorie principale)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' AND COLUMN_NAME = 'parent_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE categories ADD COLUMN parent_id INT NULL AFTER slug',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' AND COLUMN_NAME = 'description');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE categories ADD COLUMN description TEXT NULL AFTER parent_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' AND COLUMN_NAME = 'display_order');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE categories ADD COLUMN display_order INT NOT NULL DEFAULT 0 AFTER is_active',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' AND COLUMN_NAME = 'created_at');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE categories ADD COLUMN created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP AFTER display_order',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' AND COLUMN_NAME = 'updated_at');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE categories ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Index sur parent_id + ordre d'affichage (si absents)
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' AND INDEX_NAME = 'idx_categories_parent');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE categories ADD INDEX idx_categories_parent (parent_id, display_order, name)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Clé étrangère parent_id -> categories.id
-- ON DELETE SET NULL : la suppression d'un parent ne supprime JAMAIS
-- toute l'arborescence (les enfants remontent au niveau racine).
SET @fk_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' AND CONSTRAINT_NAME = 'fk_categories_parent');
SET @ddl = IF(@fk_exists = 0,
  'ALTER TABLE categories ADD CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id) ON DELETE SET NULL ON UPDATE CASCADE',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Élargir name/slug si besoin (noms longs FR)
ALTER TABLE categories MODIFY COLUMN name VARCHAR(150) NOT NULL;
ALTER TABLE categories MODIFY COLUMN slug VARCHAR(150) NOT NULL;
