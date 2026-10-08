-- =========================================================
-- BAZAAR — Migration Relevés PawaPay (Finances).
-- Idempotent : ré-exécutable sans erreur.
--
-- Un relevé est ASYNC : statementId généré PAR PawaPay (persisté APRÈS
-- l'appel ACCEPTED), statut final par polling/callback. downloadUrl
-- expire après 1 jour (doc) : toujours revérifier avant usage.
-- AUCUNE écriture comptable n'en découle (diagnostic/audit uniquement).
-- =========================================================

CREATE TABLE IF NOT EXISTS pawapay_statements (
  id INT NOT NULL AUTO_INCREMENT,
  statement_id VARCHAR(36) NOT NULL COMMENT 'statementId PawaPay (UUID, généré par PawaPay)',
  status ENUM('ACCEPTED','PROCESSING','COMPLETED','FAILED') NOT NULL DEFAULT 'ACCEPTED',
  wallet_country VARCHAR(3) NOT NULL,
  wallet_currency VARCHAR(10) NOT NULL,
  wallet_provider VARCHAR(30) NULL,
  start_date VARCHAR(30) NOT NULL COMMENT 'RFC3339',
  end_date VARCHAR(30) NOT NULL COMMENT 'RFC3339',
  callback_url VARCHAR(500) NOT NULL,
  download_url VARCHAR(1000) NULL COMMENT 'Expire après 1 jour (downloadUrlExpiresAt)',
  download_url_expires_at VARCHAR(30) NULL,
  completed_at VARCHAR(30) NULL,
  failed_at VARCHAR(30) NULL,
  failure_reason VARCHAR(255) NULL,
  requested_by INT NULL COMMENT 'Admin demandeur (admins.id, informatif)',
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  last_reconciled_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_statement_id (statement_id),
  KEY idx_statement_status (status),
  KEY idx_statement_wallet (wallet_country, wallet_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
