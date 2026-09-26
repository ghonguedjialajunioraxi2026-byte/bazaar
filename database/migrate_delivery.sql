-- =========================================================
-- MBOA STYLE — Migration livraison Phase 12
-- Expéditions internes (aucun transporteur externe) : 1 commande
-- (mono-fournisseur par construction) = 1 expédition. Historique
-- d'événements immuable. Idempotent.
-- =========================================================

CREATE TABLE IF NOT EXISTS shipments (
  id              INT AUTO_INCREMENT PRIMARY KEY,
  order_id        INT NOT NULL UNIQUE COMMENT '1 commande = 1 expédition',
  tracking_number VARCHAR(24) NOT NULL UNIQUE COMMENT 'MBOA-TRK-XXXXXXXX',
  carrier         VARCHAR(40) NOT NULL DEFAULT 'INTERNAL' COMMENT 'INTERNAL par défaut, extensible',
  status          ENUM('PREPARING','READY_FOR_PICKUP','PICKED_UP','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED','CANCELLED') NOT NULL DEFAULT 'PREPARING',
  failure_reason  VARCHAR(255) NULL,
  shipped_at      TIMESTAMP NULL,
  delivered_at    TIMESTAMP NULL,
  created_at      TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_ship_order FOREIGN KEY (order_id)
    REFERENCES orders (id) ON DELETE CASCADE,
  INDEX idx_ship_status (status, created_at),
  INDEX idx_ship_track (tracking_number)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS shipment_events (
  id          INT AUTO_INCREMENT PRIMARY KEY,
  shipment_id INT NOT NULL,
  status      VARCHAR(30) NOT NULL,
  label       VARCHAR(255) NOT NULL,
  detail      VARCHAR(500) NULL,
  source      ENUM('SUPPLIER','ADMIN','CLIENT','SYSTEM') NOT NULL DEFAULT 'SYSTEM',
  created_at  TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ev_ship FOREIGN KEY (shipment_id)
    REFERENCES shipments (id) ON DELETE CASCADE,
  INDEX idx_ev_ship (shipment_id, id)
) ENGINE=InnoDB;
