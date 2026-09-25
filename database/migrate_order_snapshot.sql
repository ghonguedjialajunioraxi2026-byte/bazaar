-- =========================================================
-- MBOA STYLE — Migration snapshot commande (Phase 7)
-- Justification (§8) : l'historique ne doit pas dépendre du nom
-- actuel du produit. order_items.product_name fige le nom au moment
-- de l'achat. Idempotent : ré-exécutable sans erreur.
-- =========================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND COLUMN_NAME = 'product_name');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE order_items ADD COLUMN product_name VARCHAR(150) NULL AFTER product_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Rattrapage historique : nom actuel pour les lignes existantes
UPDATE order_items oi
  LEFT JOIN products p ON p.id = oi.product_id
  SET oi.product_name = p.name
  WHERE oi.product_name IS NULL AND p.name IS NOT NULL;

-- Unicité du numéro de commande (généré CMD-AAAA-XXXXX)
SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'uq_orders_number');
SET @ddl2 = IF(@idx_exists = 0,
  'ALTER TABLE orders ADD UNIQUE KEY uq_orders_number (order_number)',
  'SELECT 1');
PREPARE stmt2 FROM @ddl2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;
