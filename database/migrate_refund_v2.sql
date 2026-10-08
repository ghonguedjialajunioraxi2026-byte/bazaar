-- =========================================================
-- BAZAAR — Migration Refund v2 (5 capacités PawaPay).
-- Idempotent : ré-exécutable sans erreur. Appliquer APRÈS
-- migrate_payout_v2.sql. Données existantes préservées.
--
--  1) refunds.status += ACCEPTED, ENQUEUED (statuts distants PawaPay
--     stockés tels quels, jamais convertis arbitrairement ; requis pour
--     cibler Cancel Enqueued : POST /v2/refunds/fail-enqueued/{id}).
-- =========================================================

ALTER TABLE refunds
  MODIFY COLUMN status ENUM(
    'PENDING','PROCESSING','COMPLETED','FAILED',
    'ACCEPTED','ENQUEUED'
  ) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING';
