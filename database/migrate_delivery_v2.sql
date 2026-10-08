-- =========================================================
-- MBOA STYLE / BAZAAR — Livraison v2 : workflow en 2 actions
--   DELIVERY_STARTED (fournisseur en route)
--   CLIENT_RECEIPT_CONFIRMED (client a reçu, flag receipt_confirmed existant)
--   DELIVERY_VALIDATED (fournisseur valide -> payout)
--
-- Réutilise shipments + shipment_events (historique/audit).
-- Les statuts ENUM existants sont CONSERVÉS (paiements/ledger en dépendent).
-- Idempotent : chaque ALTER est protégé par une vérification
-- INFORMATION_SCHEMA (ré-exécutable sans erreur).
-- Rollback : ALTER TABLE shipments DROP COLUMN <col> (après
-- sauvegarde, voir scripts/backup-db.sh).
-- =========================================================

SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments'
    AND COLUMN_NAME = 'delivery_started_at'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE shipments ADD COLUMN delivery_started_at TIMESTAMP NULL DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments'
    AND COLUMN_NAME = 'delivery_started_by'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE shipments ADD COLUMN delivery_started_by VARCHAR(20) NULL DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments'
    AND COLUMN_NAME = 'delivery_validated_at'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE shipments ADD COLUMN delivery_validated_at TIMESTAMP NULL DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments'
    AND COLUMN_NAME = 'delivery_validated_by'
);
SET @ddl := IF(@col_exists = 0,
  'ALTER TABLE shipments ADD COLUMN delivery_validated_by VARCHAR(20) NULL DEFAULT NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill depuis l'existant (n'écrase jamais une valeur déjà posée) :
-- une expédition déjà en transit/livrée = livraison démarrée.
UPDATE shipments
  SET delivery_started_at = COALESCE(shipped_at, delivered_at, created_at),
      delivery_started_by = COALESCE(delivery_started_by, 'SUPPLIER')
  WHERE delivery_started_at IS NULL
    AND status IN ('PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED');

-- Une expédition livrée = livraison validée (ancien workflow).
UPDATE shipments
  SET delivery_validated_at = COALESCE(delivered_at, updated_at),
      delivery_validated_by = COALESCE(delivery_validated_by, 'SUPPLIER')
  WHERE delivery_validated_at IS NULL
    AND status = 'DELIVERED';
