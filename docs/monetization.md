# MBOA STYLE — Monétisation (Phase 10, sans PSP — Phase 11)

## Modèle économique
Commission % + frais fixes par commande, configurables
(`monetization_rules` : `commission_bps`, `fixed_fee`, fenêtre de dates).
Défaut historique : 10 %, 0 frais. Pas d'abonnement/boost implémenté
(architecture prête : règles extensibles).

## Base de calcul (documentée)
Montant **réellement transacté** (`order_items.qty × unit_price`,
prix négociés inclus) — jamais le catalogue. Remise = écart catalogue
uniquement si offre liée. Règle **figée par écriture** (anti-rétroactivité).

## Monnaie / arrondis
FCFA entier (indivisible) : arithmétique entière, taux en points de base,
`commission = (net × bps + 5000) / 10000` (HALF_UP). Pas de BigDecimal
nécessaire (justifié) ; pas de `double` dans les calculs.

## Ledger (`financial_ledger`, immuable)
`LED-<order>-SALE|REVERSAL`, montants **signés**, statuts EARNED/REVERSED,
UNIQUE(reference) = idempotence. Comptabilisé au passage LIVREE
(retry sûr). Annulation EN_ATTENTE : rien. Delete commande comptabilisée :
409. Remboursement = REVERSAL via `POST /api/admin/orders/refund`
(aucun mouvement d'argent). Soldes (`suppliers.balance`) maintenus dans
la même transaction ; `rebuildBalances()` + endpoint de recalcule.

## Sécurité
Isolation (soldes/écritures scopés), prix/fournisseur recalculés serveur,
verrous admin. Endpoints : `/api/admin/finance`, `/rules…`,
`/finance/rebuild`, `/supplier/ledger`, `/supplier/revenue` (ledger).

## Limites
Pas de PSP/retraits/paiements (Phase 11) ; `pending/paid_balance`
non gérés ; pas d'abonnement/boost ; pas de stats temps réel.
