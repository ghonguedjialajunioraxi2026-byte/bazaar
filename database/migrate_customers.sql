-- =========================================================
-- BAZAAR — Comptes clients légers (nom + email, sans mot de passe).
-- Authentification : session token Bearer 30 jours (même pattern que
-- supplier_sessions / admin_sessions). Google : sub lié au compte.
-- orders.customer_id : traçabilité commande→client (NULL = historique invité).
-- Idempotent : ré-exécutable sans erreur.
-- =========================================================

CREATE TABLE IF NOT EXISTS customers (
  id INT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(160) NOT NULL,
  google_sub VARCHAR(64) NULL COMMENT 'sub Google vérifié (tokeninfo)',
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_customers_email (email),
  UNIQUE KEY uq_customers_google_sub (google_sub)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_sessions (
  id INT NOT NULL AUTO_INCREMENT,
  customer_id INT NOT NULL,
  token VARCHAR(64) NOT NULL COMMENT 'Bearer 48 hex',
  ip_address VARCHAR(45) NULL,
  user_agent VARCHAR(255) NULL,
  expires_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_customer_session_token (token),
  KEY idx_customer_session_customer (customer_id),
  CONSTRAINT fk_customer_session_customer FOREIGN KEY (customer_id)
    REFERENCES customers (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'customer_id');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE orders ADD COLUMN customer_id INT NULL AFTER supplier_id, ADD CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (id) ON DELETE SET NULL',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_customer');
SET @ddl = IF(@idx_exists = 0,
  'ALTER TABLE orders ADD KEY idx_orders_customer (customer_id)',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
