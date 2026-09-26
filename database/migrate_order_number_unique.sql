-- =========================================================
-- MBOA STYLE — Unicité des numéros de commande (Phase 15)
-- Exécuter via : mysql -u root -p mboa_style < database/migrate_order_number_unique.sql
-- Verrouille la génération MAX+1 contre les doublons concurrents
-- (OrderDAO rejoue la transaction en cas de conflit).
-- =========================================================

-- Déduplique l'historique éventuel avant contrainte (suffixe -id).
UPDATE orders o
JOIN (
  SELECT order_number, MIN(id) AS keep_id
  FROM orders
  WHERE order_number IS NOT NULL
  GROUP BY order_number
  HAVING COUNT(*) > 1
) d ON d.order_number = o.order_number AND o.id <> d.keep_id
SET o.order_number = CONCAT(o.order_number, '-', o.id);

SET @has_udx := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'uq_orders_number');
SET @sql := IF(@has_udx = 0,
  'ALTER TABLE orders ADD UNIQUE INDEX uq_orders_number (order_number)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
