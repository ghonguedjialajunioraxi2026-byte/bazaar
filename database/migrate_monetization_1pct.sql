-- =========================================================
-- MBOA STYLE / BAZAAR — Commission BAZAAR = 1 %
-- Garantit qu'exactement UNE règle à 1 % (100 bps) est active,
-- sans toucher aux écritures déjà figées dans financial_ledger
-- (taux figé par commande : aucune rétroactivité).
--
-- Contexte : migrate_monetization.sql semait « Standard 10 % »
-- (1000 bps) ; migrate_payout_refund.sql sème déjà
-- « BAZAAR Standard 1 % ». Cette migration répare les bases où
-- une règle ≠ 1 % serait restée active (ordre d'application,
-- règle personnalisée réactivée).
--
-- Idempotent : ré-exécutable sans erreur ni doublon.
-- Appliquer après database/migrate_monetization.sql :
--   mysql -u root -p mboa_style < database/migrate_monetization_1pct.sql
-- Rollback : réactiver l'ancienne règle par id
--   (UPDATE monetization_rules SET is_active = 1 WHERE id = <ancien>).
-- =========================================================

-- 1. Désactive les règles actives qui ne sont pas à 1 %.
UPDATE monetization_rules
  SET is_active = 0
  WHERE is_active = 1 AND commission_bps <> 100;

-- 2. S'il n'existe aucune règle active, réactive la plus récente à 1 %.
SET @onepct := (SELECT MAX(id) FROM monetization_rules WHERE commission_bps = 100);
SET @anyactive := (SELECT COUNT(*) FROM monetization_rules WHERE is_active = 1);
UPDATE monetization_rules SET is_active = 1
  WHERE id = @onepct AND @onepct IS NOT NULL AND @anyactive = 0;

-- 3. Sinon (aucune règle à 1 % du tout), crée « Standard 1 % » active.
INSERT INTO monetization_rules (name, commission_bps, fixed_fee, is_active)
SELECT 'Standard 1 %', 100, 0, 1 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM monetization_rules WHERE commission_bps = 100);

-- 4. Taux d'affichage fournisseur : 10.00 (legacy) -> 1.00.
-- Ne touche PAS aux taux personnalisés (toute autre valeur conservée).
UPDATE suppliers SET commission_rate = 1.00 WHERE commission_rate = 10.00;

-- 5. Défaut des nouvelles boutiques : 1 %.
ALTER TABLE suppliers MODIFY COLUMN commission_rate DECIMAL(5,2) NOT NULL DEFAULT 1.00;
