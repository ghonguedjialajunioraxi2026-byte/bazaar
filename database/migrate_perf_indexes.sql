-- =========================================================
-- MBOA STYLE — Index de performance (Phase 17)
-- Exécuter via : mysql -u root -p mboa_style < database/migrate_perf_indexes.sql
-- Chaque index est justifié par une requête EXPLAINée (docs/performance.md).
-- Tables petites : coût INSERT/UPDATE négligeable, lectures ciblées.
-- Tous idempotents (IF NOT EXISTS logique via INFORMATION_SCHEMA).
-- =========================================================

-- 1) orders par téléphone (historique invité : myorders, track)
--    AVANT : ALL (full scan). APRÈS : ref sur idx_orders_phone.
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_phone');
SET @s := IF(@h = 0, 'ALTER TABLE orders ADD INDEX idx_orders_phone (phone)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 2) orders par statut + tri date (admin : filtres, GROUP BY status)
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_status');
SET @s := IF(@h = 0, 'ALTER TABLE orders ADD INDEX idx_orders_status (status, created_at)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 3) orders par fournisseur (espace fournisseur : commandes, dashboard)
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_supplier');
SET @s := IF(@h = 0, 'ALTER TABLE orders ADD INDEX idx_orders_supplier (supplier_id, created_at)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 4) orders triés par date (findAll LIMIT 500, dernières commandes)
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_created');
SET @s := IF(@h = 0, 'ALTER TABLE orders ADD INDEX idx_orders_created (created_at)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 5) payments par statut (supervision admin, overview)
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND INDEX_NAME = 'idx_payments_status');
SET @s := IF(@h = 0, 'ALTER TABLE payments ADD INDEX idx_payments_status (status, created_at)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 6) messages non lus (compteurs + supervision : WHERE sender AND is_read)
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messages' AND INDEX_NAME = 'idx_messages_sender_read');
SET @s := IF(@h = 0, 'ALTER TABLE messages ADD INDEX idx_messages_sender_read (sender, is_read)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 7) conversations triées par activité (listes + supervision ORDER BY updated_at)
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversations' AND INDEX_NAME = 'idx_conv_updated');
SET @s := IF(@h = 0, 'ALTER TABLE conversations ADD INDEX idx_conv_updated (updated_at)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 8) audit paginé par action + date (journal admin)
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'activity_logs' AND INDEX_NAME = 'idx_logs_action_created');
SET @s := IF(@h = 0, 'ALTER TABLE activity_logs ADD INDEX idx_logs_action_created (action, created_at)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 9) avis produit approuvés triés (fiche produit : product_id + approved + date)
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reviews' AND INDEX_NAME = 'idx_reviews_product_approved');
SET @s := IF(@h = 0, 'ALTER TABLE reviews ADD INDEX idx_reviews_product_approved (product_id, approved, created_at)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 10) demandes de retrait en attente (overview : WHERE status IN)
SET @h := (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND INDEX_NAME = 'idx_sp_status');
SET @s := IF(@h = 0, 'ALTER TABLE supplier_payments ADD INDEX idx_sp_status (status)', 'SELECT 1');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
