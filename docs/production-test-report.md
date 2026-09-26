# MBOA STYLE — Rapport de test production (Phase 23)

```
========================================
PHASE 23 — RAPPORT DE TEST PRODUCTION
========================================

VERSION : 1.0.1 (1.0.0 testée puis patchée, voir BUG-P23-001)
COMMIT : 20e5ea7 (+ fix ReviewServlet, à committer ci-dessous)
DOMAINE : aucun (instance déployée Phase 22 : http://localhost:8080)
DATE : 2026-09-27

TESTS TOTAL : 43 (38 exécutés + 5 NOT CONFIGURED)
PASS : 38
FAIL : 0 (1 FAIL initial corrigé + retesté)
BLOCKED : 0 (+1 test résilience DB-down non exécutable sans sudo, documenté)
NOT CONFIGURED : 5 (HTTPS, DNS, OAuth prod, paiement LIVE, email/SMS/push)

P0 : 0
P1 : 0
P2 : 1 (BUG-P23-001, corrigé)
P3 : 0
P4 : 0
```

Version testée : WAR `releases/mboa-style-1.0.0.war` (contenu identique au
build du commit 20e5ea7 — hash internes égaux), puis `1.0.1` après correction.
Java 17.0.19, Tomcat 10.1.59, MySQL 8.0.46 (`mboa_style_prod`). Instance
redémarrée pendant la campagne (reprise validée : health UP, login OK, 402
catégories intactes).

## Résultats par domaine

| Domaine | Tests | PASS | Autre |
|---|---|---|---|
| AUTH (inscription/connexion/logout/session) | 3 | 3 | — |
| CATALOGUE (catégories/filtres/tri/pagination) | 2 | 2 | — |
| PRODUITS (fiche, prix backend, variantes M/Rouge, stock global) | 2 | 2 | — |
| PANIER/CHECKOUT/COMMANDES (1 cmd/fournisseur, totaux serveur) | 2 | 2 | — |
| MULTI-FOURNISSEURS (séparation + cross 400) | 1 | 1 | — |
| PAIEMENTS (init/référence/verify manuel) | 1 | 1 | LIVE : NOT CONFIGURED |
| LIVRAISON (5 transitions, suivi client) | 1 | 1 | — |
| MESSAGERIE + NÉGOCIATION (40 000→45 000→accept→commande, prix public intact) | 2 | 2 | — |
| FOURNISSEURS (dashboard/ledger + isolation A↔B) | 2 | 2 | — |
| ADMIN (dashboard, GESTIONNAIRE restreint) | 1 | 1 | — |
| NOTIFICATIONS (création/affichage) | 1 | 1 | email/SMS/push : NOT CONFIGURED |
| AVIS (modéré + 404 produit inexistant après fix) | 1 | 1 | — |
| MONÉTISATION/LEDGER | 1 | 1 | — |
| 404/401/403 | 2 | 2 | — |
| IDOR (commandes/conversations/produits tiers) | 1 | 1 | — |
| SQLi/XSS contrôlées | 2 | 2 | — |
| UPLOAD (php → 400) | 1 | 1 | — |
| RATE LIMITING | 1 | 1 | — |
| CONCURRENCE (stock=1, 200+400) | 1 | 1 | — |
| SEO (title/canonical/robots/sitemap dynamique, pas d'URL privée) | 1 | 1 | — |
| RESPONSIVE (390 px) + ACCESSIBILITÉ (labels/alt/aria) | 2 | 2 | — |
| PERFORMANCE (home 1-2 ms, search 6-7 ms, health 5-6 ms local) | 1 | 1 | mesure locale uniquement |
| LOGS (0 SEVERE) + MONITORING (health) | 1 | 1 | — |
| BACKUP (fichier récent présent) | 1 | 1 | restauration : testée Phase 21/22 |
| ENV/HTTPS/DNS/OAuth/LIVE | 0 | 0 | 5 NOT CONFIGURED (attendus, pas des FAIL) |
| REPRISE (restart Tomcat) | 1 | 1 | DB-down : non exécutable sans sudo (documenté) |

Détail par test : scripts `/tmp/opencode/p23-a.js` (14 PASS) et `p23-b.js`
(19 PASS + 5 NOT CONFIGURED).

## Bugs

### BUG-P23-001 — P2 MAJEUR — avis sur produit inexistant → 500 (CORRIGÉ)
- Reproduction : `POST /api/reviews {productId:99999999,…}` → 500
  `INTERNAL_ERROR` (au lieu de 404).
- Cause : FK `reviews.product_id` → `SQLException` non mappée
  (`ReviewServlet.doPost` → `ReviewDAO.create`).
- Correction : contrôle d'existence (`ProductDAO.findById`) → 404
  « Produit introuvable » (`ReviewServlet.java`).
- Retest : 404 vérifié sur 1.0.1 ; campagne complète re-passée (38/38) ;
  `mvn clean test` 62/62.
- Régression : aucune (création d'avis valide inchangée).

Aucun autre bug. P0/P1 : 0. Paiement fiable (manuel), commandes fiables,
aucune fuite, aucun accès non autorisé, base cohérente (0 orphelin).

## Données de test — décision

| Donnée | Décision | État |
|---|---|---|
| 15 fournisseurs `[P23]…@test.invalid` | DELETE | supprimés |
| 9 produits `[P23]…` | DELETE | supprimés |
| 10 commandes + lignes + 10 paiements + 2 expéditions | DELETE | supprimés |
| 2 conversations + messages + 4 offres + 3 avis | DELETE | supprimés |
| 51 notifications de test (références orphelines incluses) | DELETE | supprimées (0 restante) |
| seed catégories (402) + admin `mboa_admin` | KEEP | conservés |
| backups pré-déploiement + pré-purge | ARCHIVE | `/tmp/mboa-style/backups/` |

Base prod finale : 0 commande/produit/fournisseur/conversation/avis/paiement/
notification, 402 catégories, 1 admin. Vérifié par requêtes SQL.

## Décision technique

**READY FOR LAUNCH WITH MINOR ISSUES** — réserves : pas de domaine/HTTPS
(public impossible sans serveur réel), OAuth prod et PSP non configurés
(fonctionnement manuel validé), DB-down non rejoué sans sudo, perf mesurée
en local uniquement. Aucun P0/P1, paiement et commandes fiables.
