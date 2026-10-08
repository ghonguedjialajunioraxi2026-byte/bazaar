-- =========================================================
-- BAZAAR — Prix de livraison par produit + demandes de catégories.
-- 1) products.delivery_price (FCFA) : saisi par le fournisseur.
--    0 = tarif de la zone par ville (comportement conservé).
--    Règle commande (mono-fournisseur, calcul serveur) : frais = max
--    des prix saisis sur les lignes (> 0), sinon tarif zone.
-- 2) category_requests : demandes fournisseurs (validation admin).
-- Idempotent : ré-exécutable sans erreur.
-- =========================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'delivery_price');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE products ADD COLUMN delivery_price INT NOT NULL DEFAULT 0 AFTER stock',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS category_requests (
  id INT NOT NULL AUTO_INCREMENT,
  supplier_id INT NOT NULL,
  name VARCHAR(120) NOT NULL,
  parent_id INT NULL COMMENT 'NULL = catégorie principale, sinon sous-catégorie',
  status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  admin_note VARCHAR(255) NULL,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_catreq_supplier (supplier_id),
  KEY idx_catreq_status (status),
  CONSTRAINT fk_catreq_supplier FOREIGN KEY (supplier_id)
    REFERENCES suppliers (id) ON DELETE CASCADE,
  CONSTRAINT fk_catreq_parent FOREIGN KEY (parent_id)
    REFERENCES categories (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
