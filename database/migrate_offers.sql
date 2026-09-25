-- =========================================================
-- MBOA STYLE — Migration négociation Phase 8
-- Justification : offres de prix traçables (jamais écrasées),
-- isolées du prix catalogue, consommables une seule fois.
-- Idempotent : ré-exécutable sans erreur.
-- =========================================================

CREATE TABLE IF NOT EXISTS offers (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  conversation_id INT NOT NULL,
  product_id      INT NOT NULL,
  qty             INT NOT NULL DEFAULT 1,
  size            VARCHAR(20) NULL,
  color           VARCHAR(40) NULL,
  catalog_price   INT NOT NULL COMMENT 'Prix catalogue au moment de l offre',
  unit_price      INT NOT NULL COMMENT 'Prix proposé (offre)',
  created_by      ENUM('CLIENT','SUPPLIER') NOT NULL,
  status          ENUM('PENDING','ACCEPTED','REJECTED','COUNTERED','CONSUMED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  parent_offer_id INT NULL COMMENT 'Offre à laquelle celle-ci répond',
  created_at      TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_offers_conversation FOREIGN KEY (conversation_id)
    REFERENCES conversations (id) ON DELETE CASCADE,
  CONSTRAINT fk_offers_product FOREIGN KEY (product_id)
    REFERENCES products (id),
  CONSTRAINT fk_offers_parent FOREIGN KEY (parent_offer_id)
    REFERENCES offers (id) ON DELETE SET NULL,
  INDEX idx_offers_conv (conversation_id, created_at),
  INDEX idx_offers_status (status)
) ENGINE=InnoDB;

-- Traçabilité offre -> commande (prix négocié conservé dans unit_price)
SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND COLUMN_NAME = 'offer_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE order_items ADD COLUMN offer_id INT NULL AFTER product_name, ADD CONSTRAINT fk_items_offer FOREIGN KEY (offer_id) REFERENCES offers (id) ON DELETE SET NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
