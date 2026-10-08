-- =========================================================
-- BAZAAR — Migration Payout PawaPay + Refund + Litiges + Commission 1 %
-- Idempotent : ré-exécutable sans erreur. Appliquer APRÈS
-- migrate_pawapay_deposit.sql. Données existantes préservées.
--
-- Contenu :
--  1) platform_settings (frais PawaPay Payout configurables)
--  2) Commission BAZAAR 1 % (nouvelle règle active 100 bps)
--  3) Coordonnées de versement fournisseur (suppliers)
--  4) Payouts PawaPay (extension supplier_payments, 1 payout/commande)
--  5) Refunds PawaPay (table refunds)
--  6) Litiges client NOT_RECEIVED (table disputes)
--  7) Confirmation de réception traçable (shipments)
-- =========================================================

-- ------------------------------------------------------------------
-- 1) Paramètres plateforme (clé/valeur, traçables, modifiables admin)
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS platform_settings (
  setting_key VARCHAR(80) NOT NULL,
  setting_value VARCHAR(255) NOT NULL,
  updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Frais PawaPay appliqués au Payout (XAF, à confirmer sur le dashboard
-- PawaPay du compte BAZAAR ; 0 par défaut = aucun frais prélevé).
INSERT INTO platform_settings (setting_key, setting_value)
  VALUES ('pawapay.payout.fee', '0')
  ON DUPLICATE KEY UPDATE setting_value = setting_value;

-- ------------------------------------------------------------------
-- 2) Commission BAZAAR = 1 % (100 bps). Les écritures ledger existantes
-- gardent leur taux figé (anti-rétroactivité) ; seules les nouvelles
-- ventes utilisent 1 %. Règle modifiable via /api/admin/monetization/rules.
-- ------------------------------------------------------------------
UPDATE monetization_rules SET is_active = 0 WHERE is_active = 1;

INSERT INTO monetization_rules (name, commission_bps, fixed_fee, is_active, starts_at, ends_at)
  SELECT 'BAZAAR Standard 1 %', 100, 0, 1, NULL, NULL
  FROM DUAL
  WHERE NOT EXISTS (
    SELECT 1 FROM monetization_rules
    WHERE commission_bps = 100 AND fixed_fee = 0 AND is_active = 1
  );

-- Affichage fournisseur cohérent (colonne informative, le ledger fait foi).
UPDATE suppliers SET commission_rate = 1.00 WHERE commission_rate <> 1.00;
ALTER TABLE suppliers
  MODIFY COLUMN commission_rate DECIMAL(5,2) NOT NULL DEFAULT 1.00;

-- ------------------------------------------------------------------
-- 3) Coordonnées de versement Mobile Money du fournisseur
-- (provider PawaPay explicite : MTN_MOMO_CMR / ORANGE_CMR — jamais deviné
-- depuis le numéro, les préfixes se chevauchent au Cameroun).
-- ------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suppliers' AND COLUMN_NAME = 'payout_provider');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE suppliers ADD COLUMN payout_provider VARCHAR(30) NULL AFTER commission_rate',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'suppliers' AND COLUMN_NAME = 'payout_phone');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE suppliers ADD COLUMN payout_phone VARCHAR(20) NULL AFTER payout_provider',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 4) Payouts : réutilise supplier_payments (pas de table en double).
-- Une ligne "auto" = 1 commande (order_id UNIQUE), traçabilité complète
-- brut/commission/frais/net + payoutId PawaPay.
-- ------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'order_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN order_id INT NULL AFTER supplier_id, ADD CONSTRAINT fk_sp_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND INDEX_NAME = 'uq_sp_order');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE supplier_payments ADD UNIQUE KEY uq_sp_order (order_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'payout_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN payout_id VARCHAR(36) NULL AFTER order_id, ADD UNIQUE KEY uq_sp_payout (payout_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'provider');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN provider VARCHAR(30) NULL AFTER payout_id',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'phone_number');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN phone_number VARCHAR(20) NULL AFTER provider',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'gross_amount');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN gross_amount INT NULL AFTER phone_number',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'commission');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN commission INT NULL AFTER gross_amount',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'pawapay_fee');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN pawapay_fee INT NULL AFTER commission',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND COLUMN_NAME = 'failure_reason');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE supplier_payments ADD COLUMN failure_reason VARCHAR(255) NULL AFTER pawapay_fee',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Statuts PawaPay (COMPLETED final) + gestion litige (BLOCKED/CANCELLED),
-- historique manuel (PENDING/PROCESSING/PAID/FAILED) conservé tel quel.
ALTER TABLE supplier_payments
  MODIFY COLUMN status ENUM(
    'PENDING','PROCESSING','PAID','FAILED',
    'COMPLETED','CANCELLED','BLOCKED'
  ) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING';

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'supplier_payments' AND INDEX_NAME = 'idx_sp_order');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE supplier_payments ADD KEY idx_sp_order (order_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 5) Refunds PawaPay (POST /v2/refunds {refundId, depositId}).
-- 1 refund actif par commande : unicité applicative (refundId UNIQUE +
-- garde "refund non final existant" dans le service).
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refunds (
  id INT NOT NULL AUTO_INCREMENT,
  order_id INT NOT NULL,
  payment_id INT NOT NULL COMMENT 'Dépôt PawaPay remboursé (payments.id)',
  refund_id VARCHAR(36) NOT NULL COMMENT 'refundId PawaPay (UUIDv4)',
  deposit_id VARCHAR(36) NOT NULL COMMENT 'depositId PawaPay remboursé',
  amount INT NOT NULL COMMENT 'Montant remboursé (FCFA)',
  currency VARCHAR(10) NOT NULL DEFAULT 'XAF',
  status ENUM('PENDING','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'PENDING',
  failure_reason VARCHAR(255) NULL,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_refund_id (refund_id),
  KEY idx_refund_order (order_id),
  KEY idx_refund_deposit (deposit_id),
  KEY idx_refund_status (status),
  CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- 6) Litiges "commande non reçue" (protection acheteur).
-- Tant qu'un litige est OPEN : PAYOUT = BLOCKED.
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS disputes (
  id INT NOT NULL AUTO_INCREMENT,
  order_id INT NOT NULL,
  phone VARCHAR(20) NOT NULL COMMENT 'Téléphone déclarant (propriété vérifiée)',
  reason VARCHAR(30) NOT NULL DEFAULT 'NOT_RECEIVED',
  status ENUM('OPEN','RESOLVED_REFUNDED','RESOLVED_REJECTED') NOT NULL DEFAULT 'OPEN',
  resolution_note VARCHAR(500) NULL,
  resolved_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_dispute_order (order_id),
  KEY idx_dispute_status (status),
  CONSTRAINT fk_dispute_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------------
-- 7) Confirmation de réception traçable (condition du Payout).
-- receipt_confirmed = 1 exige un acte client (ou admin) daté et signé.
-- ------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'receipt_confirmed');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE shipments ADD COLUMN receipt_confirmed TINYINT(1) NOT NULL DEFAULT 0 AFTER delivered_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'receipt_confirmed_at');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE shipments ADD COLUMN receipt_confirmed_at TIMESTAMP NULL DEFAULT NULL AFTER receipt_confirmed',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shipments' AND COLUMN_NAME = 'receipt_confirmed_by');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE shipments ADD COLUMN receipt_confirmed_by VARCHAR(20) NULL AFTER receipt_confirmed_at',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------------
-- 8) Garde anti double-paiement au niveau BASE (race check-then-insert).
-- open_guard = order_id pour les lignes ouvertes, NULL sinon ; les NULL
-- multiples sont autorisés par MySQL → au plus 1 tentative OUVERTE
-- (PENDING/PROCESSING) par commande. Les doublons concurrents lèvent
-- une violation d'unicité, rattrapée par le service qui retourne la
-- ligne existante (aucun 2ᵉ appel PawaPay).
-- ------------------------------------------------------------------

-- 8a) Résorber d'éventuels doublons ouverts historiques (lignes PENDING
-- non financières : on garde la plus récente, les autres → FAILED).
UPDATE payments p
  JOIN (
    SELECT order_id, MAX(id) AS keep_id
    FROM payments
    WHERE provider = 'PAWAPAY' AND status IN ('PENDING','PROCESSING')
    GROUP BY order_id HAVING COUNT(*) > 1
  ) d ON d.order_id = p.order_id
  SET p.status = 'FAILED',
      p.failure_reason = 'Superseded: tentative concurrent (garde anti-doublon)'
  WHERE p.provider = 'PAWAPAY'
    AND p.status IN ('PENDING','PROCESSING')
    AND p.id <> d.keep_id;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'payments' AND COLUMN_NAME = 'open_guard');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE payments ADD COLUMN open_guard INT GENERATED ALWAYS AS (CASE WHEN status IN (''PENDING'',''PROCESSING'') THEN order_id ELSE NULL END) VIRTUAL, ADD UNIQUE KEY uq_pay_open (open_guard)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 8b) Même garde pour les remboursements (1 refund ouvert par commande).
UPDATE refunds r
  JOIN (
    SELECT order_id, MAX(id) AS keep_id
    FROM refunds
    WHERE status IN ('PENDING','PROCESSING')
    GROUP BY order_id HAVING COUNT(*) > 1
  ) d ON d.order_id = r.order_id
  SET r.status = 'FAILED',
      r.failure_reason = 'Superseded: tentative concurrent (garde anti-doublon)'
  WHERE r.status IN ('PENDING','PROCESSING')
    AND r.id <> d.keep_id;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND COLUMN_NAME = 'open_guard');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE refunds ADD COLUMN open_guard INT GENERATED ALWAYS AS (CASE WHEN status IN (''PENDING'',''PROCESSING'') THEN order_id ELSE NULL END) VIRTUAL, ADD UNIQUE KEY uq_refund_open (open_guard)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
