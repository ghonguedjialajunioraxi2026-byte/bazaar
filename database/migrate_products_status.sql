-- =========================================================
-- CAMSHOP — Migration products.status (accueil vide fix)
-- Contexte : ProductDAO référençait p.status alors que database.sql
-- ne créait pas la colonne -> /api/products en erreur 500 -> accueil vide.
-- Cette migration ajoute la colonne si absente (idempotent).
-- Exécuter via : mysql -u root -p mboa_style < database/migrate_products_status.sql
-- =========================================================
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'status');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE products ADD COLUMN status VARCHAR(20) NULL DEFAULT ''ACTIVE'' AFTER supplier_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Les produits historiques (seed) deviennent visibles partout
UPDATE products SET status = 'ACTIVE' WHERE status IS NULL;

-- Index optionnel pour les listes publiques/fournisseurs
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND INDEX_NAME = 'idx_products_status');
SET @ddl2 = IF(@idx_exists = 0,
  'ALTER TABLE products ADD INDEX idx_products_status (status)',
  'SELECT 1');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
