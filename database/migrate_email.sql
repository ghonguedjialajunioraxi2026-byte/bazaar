-- =========================================================
-- MBOA STYLE — Notifications email (sans WhatsApp).
-- Idempotent : ré-exécutable sans erreur.
-- Envoi via SMTP configuré par variables d'environnement
-- (SMTP_HOST/PORT/USER/PASS/FROM, voir .env.example).
-- Sans SMTP : les emails restent QUEUED (jamais de faux envoi).
-- =========================================================

SET @col_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'email');
SET @ddl = IF(@col_exists = 0,
  'ALTER TABLE orders ADD COLUMN email VARCHAR(160) NULL AFTER phone',
  'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS email_notifications (
  id INT NOT NULL AUTO_INCREMENT,
  recipient VARCHAR(160) NOT NULL,
  subject VARCHAR(200) NOT NULL,
  body MEDIUMTEXT NOT NULL,
  event_code VARCHAR(40) NOT NULL DEFAULT 'GENERIC',
  order_id INT NULL,
  status ENUM('QUEUED','SENT','FAILED') NOT NULL DEFAULT 'QUEUED',
  attempts INT NOT NULL DEFAULT 0,
  last_error VARCHAR(255) NULL COMMENT 'Jamais de secret (pas de mot de passe)',
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_email_status (status),
  KEY idx_email_order (order_id),
  CONSTRAINT fk_email_order FOREIGN KEY (order_id)
    REFERENCES orders (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
