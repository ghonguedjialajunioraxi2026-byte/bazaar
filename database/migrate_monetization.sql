-- =========================================================
-- MBOA STYLE — Migration monétisation Phase 10
-- Règles configurables + ledger immuable. Idempotent.
-- Monnaie : FCFA entier (indivisible) -> arithmétique entière,
-- taux en points de base, arrondi HALF_UP documenté.
-- =========================================================

CREATE TABLE IF NOT EXISTS monetization_rules (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  name            VARCHAR(120) NOT NULL,
  commission_bps  INT NOT NULL DEFAULT 1000 COMMENT 'Points de base : 1000 = 10.00 %',
  fixed_fee       INT NOT NULL DEFAULT 0 COMMENT 'Frais fixes FCFA par commande',
  is_active       TINYINT(1) NOT NULL DEFAULT 0,
  starts_at       DATETIME NULL,
  ends_at         DATETIME NULL,
  created_at      TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_rules_active (is_active, starts_at, ends_at)
) ENGINE=InnoDB;

-- Règle initiale : 10 %, sans frais fixes (conforme au défaut historique)
INSERT INTO monetization_rules (name, commission_bps, fixed_fee, is_active)
SELECT 'Standard 10 %', 1000, 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM monetization_rules);

CREATE TABLE IF NOT EXISTS financial_ledger (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  reference       VARCHAR(60) NOT NULL UNIQUE COMMENT 'Idempotence : LED-<orderId>-<kind>',
  order_id        INT NOT NULL,
  supplier_id     INT NULL,
  kind            ENUM('SALE','REVERSAL','ADJUSTMENT') NOT NULL DEFAULT 'SALE',
  gross           INT NOT NULL COMMENT 'Montant brut transacté FCFA',
  discount        INT NOT NULL DEFAULT 0 COMMENT 'Remise vs catalogue FCFA',
  net             INT NOT NULL COMMENT 'Base commission FCFA',
  commission_bps  INT NOT NULL COMMENT 'Taux figé au moment de la vente',
  commission      INT NOT NULL COMMENT 'Part MBOA FCFA',
  fixed_fee       INT NOT NULL DEFAULT 0,
  supplier_earnings INT NOT NULL COMMENT 'Part fournisseur FCFA',
  status          ENUM('EARNED','REVERSED') NOT NULL DEFAULT 'EARNED',
  created_at      TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ledger_order FOREIGN KEY (order_id)
    REFERENCES orders (id) ON DELETE CASCADE,
  CONSTRAINT fk_ledger_supplier FOREIGN KEY (supplier_id)
    REFERENCES suppliers (id) ON DELETE SET NULL,
  INDEX idx_ledger_order (order_id),
  INDEX idx_ledger_supplier (supplier_id, status, created_at)
) ENGINE=InnoDB;
