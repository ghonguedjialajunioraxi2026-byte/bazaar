# MBOA STYLE — Rapport de recette fonctionnelle (Phase 19)

Campagne exécutée le 2026-09-26 contre backend réel : Tomcat 10.1.59
(`/tmp/opencode/apache-tomcat-10.1.59`, port 8082, `DB_URL` → `mboa_style_test`,
WAR reconstruit `mvn package`) + MySQL 8.0 (`mboa_style_test` : schéma + migrations
+ seed catégories). Scripts : `/tmp/opencode/e2e-phase19.js` (51 tests HTTP),
`/tmp/opencode/e2e-admin.js` (14 tests HTTP admin/fournisseur), `/tmp/opencode/e2e-cycle.js`
(9 tests cycle complet), `/tmp/opencode/e2e-ui.js` (6 tests navigateur Chrome headless).

## Couverture : 80 tests E2E/HTTP+UI — 80 PASS, 0 FAIL, 0 BLOCKED + 58 unitaires PASS

| Domaine | Tests | PASS | FAIL | BLOCKED | PARTIAL |
|---|---|---|---|---|---|
| visiteur / navigation / SEO | 6 | 6 | 0 | 0 | 0 |
| catalogue (recherche, filtres, tri, pagination, catégories) | 8 | 8 | 0 | 0 | 0 |
| fournisseur (inscription, auth, produits, dashboard, revenus, ledger, isolation) | 9 | 9 | 0 | 0 | 0 |
| produit / SEO fiche | 1 | 1 | 0 | 0 | 0 |
| messagerie (création, message, historique, isolation cid) | 3 | 3 | 0 | 0 | 0 |
| négociation (offre, contre-offre, acceptation, négatifs) | 5 | 5 | 0 | 0 | 0 |
| commandes (+mixte multi-fournisseurs, +négatifs) | 11 | 11 | 0 | 0 | 0 |
| paiements (init, référence, idempotence, méthodes, statut, verify admin, 409) | 6 | 6 | 0 | 0 | 0 |
| livraison (création, 5 transitions, suivi, supervision) | 4 | 4 | 0 | 0 | 0 |
| cycle complet (commande→paiement→CONFIRMEE→expédition→DELIVERED→ledger→avis) | 9 | 9 | 0 | 0 | 0 |
| notifications / avis / promos / Google client-id | 5 | 5 | 0 | 0 | 0 |
| admin / rôles (dashboard, commandes, produits, paiements, MONEY 403 gest) | 8 | 8 | 0 | 0 | 0 |
| UI navigateur (rendu, BUG-001, 404, CSS, viewport) | 6 | 6 | 0 | 0 | 0 |
| unitaires `mvn clean test` | 58 | 58 | 0 | 0 | 0 |

Cycle complet validé : inscription fournisseurs → produits → conversation →
offre 4000 → contre-offre 4500 → acceptation → commande prix serveur (unitPrice=1
ignoré, total ≥ prix catalogue) → commande prix négocié → réutilisation 409 →
paiement init/référence/verify → double verify 409 → CONFIRMEE → expédition
(PREPARING→READY_FOR_PICKUP→PICKED_UP→IN_TRANSIT→OUT_FOR_DELIVERY→DELIVERED) →
commande LIVREE → ledger fournisseur 200 → avis modéré (invisible avant approbation).

Multi-fournisseur : 1 commande / vendeur OK + rejet cross-vendeur 400 (MIX-002).

Cohérence DB (`mboa_style_test`) : 0 orphelins (items, paiements, expéditions,
messages, offres) ; statuts commandes/paiements/expéditions cohérents
(EN_ATTENTE/CONFIRMEE/LIVREE, REUSSIE, DELIVERED).

## Bugs trouvés et corrigés (présents dans l'arbre de travail, vérifiés cette phase)

### BUG-001 — CRITICAL — Récursion infinie `subtreeNames` (frontend)
Préconditions : catégories aux noms dupliqués (seed : « Batteries » ×3).
Action : ouvrir l'accueil. Obtenu : `RangeError: Maximum call stack size exceeded`.
Cause : récursion par nom sans garde. Correction : parcours par ID + `seen` +
garde 5000 (`src/main/webapp/index.html:1465`). Régression : UI-001/UI-002
(rendu Chrome headless, 0 `RangeError`). Statut : PASS.

### BUG-002 — MEDIUM — `collectIds` sans garde anti-cycle (backend)
Même famille côté `CategoryService` (récursion par ID). Durci avec `seen`
(`src/main/java/com/ecommerce/service/CategoryService.java`). Statut : PASS (`mvn`).

### BUG-003 — HIGH — Commande acceptant items d'un autre vendeur
Préconditions : commande `supplierId=A` + item produit de B. Obtenu : 200
(commande visible de A avec produit de B, jamais traitable par B). Correction :
rejet 400 si item hors vendeur (`src/main/java/com/ecommerce/service/OrderService.java`).
Le frontend créait déjà 1 commande/vendeur (`createInternalOrders`, `index.html:3160`).
Régression : MIX-002 (400) + MIX-001 (2×200). Statut : PASS.

## Non-bugs constatés (comportement correct vérifié)
- Ville/quartier obligatoires au checkout (400 `Ville est obligatoire`).
- Suivi avec mauvais téléphone → 403/404 ; tracking + mauvais tél → 403/404.
- Double vérification paiement → 409 ; réutilisation offre → 409.
- Acceptation de sa propre offre → 403 (seul l'auteur adverse décide).
- Offre sans productId → 404 produit (résolution avant validation prix) ; avec
  productId, prix négatif/zéro → 400.
- GESTIONNAIRE : verify paiement → 403 ; dashboard lecture → 200.
- Avis invisibles avant approbation.
- Méthodes de paiement réelles : `PAIEMENT_LIVRAISON`, `MOBILE_MONEY_MTN`,
  `MOBILE_MONEY_ORANGE` (pas de `MTN_MOMO`).
- Statuts expédition réels : `PREPARING → READY_FOR_PICKUP → PICKED_UP →
  IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED` (+ FAILED/RETURNED/CANCELLED).
- Liste expéditions admin : `GET /api/shipments/active` (pas `/api/shipments`).
- Paiements : `POST /api/payments` avec `{action:init|submit-reference|verify|fail}`.

## Non testé / limites
- Google OAuth en vrai (bouton + contrat vérifiés : `/api/supplier/google-client-id`
  200, pas de flux réel sans credential).
- Webhooks PSP externes (aucun en local ; vérification admin testée).
- Charge multi-utilisateurs simultanés (testé séquentiel A/B + invité).
- Logs Tomcat : 0 SEVERE/Exception pendant la campagne (171 lignes
  `AbandonedConnectionCleanupThread` = bruit bénin au shutdown/redeploy uniquement).
- Données de test : comptes `supA/supB/cycle/dash@test.cm` horodatés, clients
  `65511xxx/65577xxx/65599xxx`, base `mboa_style_test` jetable. Mot de passe des
  comptes admin de TEST réinitialisé à `Admin123!` (base de test uniquement,
  jamais la prod).

## Régression finale
- `mvn clean test` : 58 tests, 0 échec. `mvn package` : WAR OK.
