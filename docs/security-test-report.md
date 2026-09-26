# MBOA STYLE — Matrice de tests sécurité + robustesse (Phase 20)

Environnement : local uniquement. Tomcat 10.1.59 (port 8082, `/tmp/opencode/...`),
`DB_URL` → MySQL 8.0 base `mboa_style_test` (données jetables), WAR reconstruit
(`mvn package`). Aucun test destructif hors `mboa_style_test`, aucune attaque
tiers, aucun vrai paiement. Scripts : `/tmp/opencode/sec1-auth.js` (31),
`sec2-inject.js` (28), `sec3-metier.js` (24), `sec4-robust.js` (28).

Légende statut : PASS (comportement attendu observé), FAIL→FIX→PASS (faille
reproduite, corrigée, retestée). Sévérités : CRITICAL/HIGH/MEDIUM/LOW/INFO.

## 1. Authentification / sessions / brute force

| ID | Test | Attendu | Obtenu | Statut | Sév. |
|---|---|---|---|---|---|
| AUTH-001 | supplier mauvais mdp | 401 | 401 | PASS | — |
| AUTH-002 | utilisateur inexistant | 401/404 | 401 | PASS | — |
| AUTH-003 | mot de passe vide | 400/401 | 401 | PASS | — |
| AUTH-004 | email invalide | 400/401 | 401 | PASS | — |
| AUTH-005 | admin mauvais mdp | 401 | 401 | PASS | — |
| BRUTE-001 | 6 échecs même compte/IP | 401/429 contrôlé | 429 (blocage 15 min, `LoginAttemptService`) | PASS | — |
| SESS-001/002/003 | token invalide/vide/absent | 401 | 401 | PASS | — |
| SESS-004 | token en query `?token=` | 401 (refusé, anti-fuite logs) | 401 | PASS | — |
| SESS-005 | logout puis réutilisation | 401 ou 200 contrôlé | 401 après logout, relogin OK | PASS | — |
| SEC-018 | session expirée sur privé | 401 | 401 | PASS | — |

## 2. Autorisation / escalade

| ID | Test | Attendu | Obtenu | Statut | Sév. |
|---|---|---|---|---|---|
| ROLE-001 | guest → /api/admin/dashboard | 401 | 401 | PASS | — |
| ROLE-002 | supplier → admin | 401/403 | 403 | PASS | — |
| ROLE-003 | GESTIONNAIRE verify paiement (MONEY) | 403 | 403 | PASS | — |
| ROLE-004 | GESTIONNAIRE DELETE produit (DESTRUCTIVE) | 403 | 403 | PASS | — |
| ROLE-005 | guest PUT produit | 401 | 401 | PASS | — |
| SEC-003 | supplier pose `role:SUPER_ADMIN` au profil | ignoré | rôle inchangé | PASS | — |
| ESC-002 | `supplierId` forgé à la création produit | ignoré (propriétaire session) | produit chez A uniquement | PASS | — |
| ESC-003 | `is_admin/role` au register | ignoré | compte supplier normal | PASS | — |

## 3. IDOR (SEC-001/002/009/010/011)

| ID | Test | Attendu | Obtenu | Statut | Sév. |
|---|---|---|---|---|---|
| SEC-001 | client A suit commande de B | 403/404 sans fuite | 404, corps sans données B | PASS | — |
| SEC-011 | myorders A ne contient pas commande B | isolation | OK | PASS | — |
| IDOR-003 | conversation B avec cid A | 401/403/404 | 403 | PASS | — |
| SEC-002a/b/c/d | A → produit/commande/conversation/stock de B | 404 | 404 (×4) | PASS | — |
| SEC-009 | `supplierId` cross-vendeur en commande | 400 | 400 (garde Phase 19) | PASS | — |
| SEC-016/017 | accès direct dashboard/fournisseur.html | page SPA, API 401 | API 401 | PASS | — |

## 4. Injection (SEC-008) / XSS (SEC-007) / CORS / headers

| ID | Test | Attendu | Obtenu | Statut | Sév. |
|---|---|---|---|---|---|
| SQLI-001..006 | 6 payloads en recherche | 200 sans fuite SQL | 200, aucune `SQLException/stack` | PASS | — |
| SQLI-010/011/012/013 | login/filtre-tri/id/track injectés | 4xx sans fuite | 401/200/404/404, `sort` whitelisté (if/else, défaut sûr) | PASS | — |
| SEC-007a | `<script>` en message | stocké, servi JSON neutre | JSON `application/json`, `escHTML` au rendu | PASS | — |
| SEC-007b | XSS en avis | invisible avant modération | non publié | PASS | — |
| XSS-003/004/005 | nom produit XSS, search, page SEO | JSON neutre / HTML échappé | `escHTML/escAttr`, pas de `<script>` injecté | PASS | — |
| CSRF-001 | POST sans Origin | API Bearer : pas de cookie → CSRF non applicable | 201/200 | PASS (INFO) | INFO |
| CORS-001/002 | Origin evil, ACAO * + credentials | jamais | aucune en-tête ACAO émise | PASS | — |
| HDR-001/002/003 | headers, fingerprint, JSON ct | nosniff/SAMEORIGIN/referrer/permissions, pas de version | OK (CSP absente par décision documentée, HSTS prod-only) | PASS | — |

## 5. Uploads (SEC-014) / traversal (SEC-015) — 1 correction

| ID | Test | Attendu | Obtenu | Statut | Sév. |
|---|---|---|---|---|---|
| SEC-014a | `evil.jpg.exe` | 400 ou neutralisé | 400 (magic bytes) | PASS | — |
| SEC-014b/c | `shell.php` / `shell.jsp` | 400 | **500 initial** → 400 après FIX-1 | FAIL→FIX→PASS | LOW |
| SEC-014d | `x.svg` | 400 ou neutralisé | 400 (SVG refusé) | PASS | — |
| SEC-015a | `/uploads/../../etc/passwd` | 400/403/404 | 404 (`..` bloqué) | PASS | — |
| SEC-015b | nom `../../evil.jpg` | 400, UUID serveur | **500 initial** → 400 après FIX-1 (nom UUID, jamais de chemin client) | FAIL→FIX→PASS | LOW |
| UPL-003 | 11 Mo (> 10 Mo) | 400/413/500 contrôlé | 400 | PASS | — |

FIX-1 : `IOException` de `UploadUtil.saveImage` (fichier refusé) → 400
« Image invalide ou refusée » au lieu de 500 (`SupplierApiServlet` doPost/doPut,
`ProductImageServlet` doPost). Aucun fichier hostile n'a jamais été écrit
(exception avant écriture, noms UUID). Retest : 28/28.

## 6. Métier : prix/stock/concurrence/paiements/livraison/négo — 1 correction

| ID | Test | Attendu | Obtenu | Statut | Sév. |
|---|---|---|---|---|---|
| SEC-004a/b/c/d | unitPrice/total/deliveryFee/discount forgés | recalcul serveur | total ≥ catalogue, fee identique | PASS (×4) | — |
| SEC-005a/b | commande > stock, qty -5/0 | 400 | 400 | PASS | — |
| SEC-005c | stock -10 | jamais < 0 en DB | 200 + clamp `Math.max(0)` en DAO → stock 0 | PASS (INFO) | INFO |
| SEC-006 | stock=1, 2 commandes // | 1 seule, stock ≥ 0 | 200+400, stock cohérent | PASS | — |
| CMD-SEC1 | client PUT statut | 401 | 401 | PASS | — |
| CMD-SEC2 | LIVREE→EN_ATTENTE | 400/409 | 400 « Transition refusée », statut inchangé | PASS | — |
| CMD-SEC3 | cancel avec tél tiers | 4xx | 403/404 | PASS | — |
| SEC-012 | double submit-reference | idempotent | 200 puis 200/409, 1 seul paiement | PASS | — |
| SEC-013a/b/c | montant forgé, commande fake, tél tiers | montant serveur / 404 / 4xx | OK | PASS | — |
| SHIP-SEC1/2/3 | track fake, avance anonyme, B→expédition A | 404 / 401 / 403 | 404 / 401 / 403 « Expédition inaccessible », état inchangé | PASS | — |
| NEG-SEC1/2 | accept par tiers, qty 999999 | 403/404, 400 | 403, 400 | PASS | — |
| MSG-SEC1/2/3 | senderId forgé, vide, spam 12/min | ignoré / 400 / 429 | 201, 400, 429 (10/min) | PASS | — |
| NOTIF-SEC1 | lecture notifs d'autrui | isolation | isolation phone | PASS | — |

## 7. Robustesse / limites (SEC-019/020) — 1 correction

| ID | Test | Attendu | Obtenu | Statut | Sév. |
|---|---|---|---|---|---|
| SEC-019 (×5) | page -1/0/huge, limit -1/huge | 200 borné ou 400 | **500 initial** (OFFSET négatif) → 200 borné (limit ≤ 50) après FIX-2 | FAIL→FIX→PASS | MEDIUM |
| SEC-020/LIM-002 | qty 999999999, 101 lignes | 400 | 400 | PASS | — |
| LIM-003/004/005 | recherche 5000c, msg 100ko, JSON 2Mo | borné / 400 | 200 (tronquée 100c) / 400 / 400 | PASS | — |
| IDS (×6) | id -1/0/abc/huge/1.5/null | propre, sans fuite | 404/200 sans stack ni SQL | PASS | — |
| JSON-001..005 | {}, invalide, tableau, champ extra, null | 400/500 propre | 400, sans stack | PASS | — |
| ERR-001/002 | 404 métier, PUT id inexistant | sans stack / 404 | OK | PASS | — |
| RATE-001/002 | 12 offres/jour/conv, 7 avis/h | 429 | 429 (10/jour, 5/h) | PASS | — |
| CONC-001 | double accept // | 1 seul effet (200+409 ou 200+200 atomique) | 200+409 (`WHERE status=PENDING` atomique) | PASS | — |
| RES-001/MOB-001 | produit supprimé, UA mobile | 404 / 200 identique | OK | PASS | — |

FIX-2 : bornes serveur `page ≥ 1`, `limit ≤ 50` (public) / `≤ 100` (admin) /
`≤ 200` (logs) via nouvel utilitaire `Pagination` (`ProductServlet`,
`CategoryServlet`, `SupplierApiServlet#ledger`, `AdminServlet`) + 4 tests
JUnit `PaginationTest`. Retest : 28/28 + `mvn` 62/62.

## 8. Scénarios critiques SEC-001..020 — synthèse

| SEC | Résultat | SEC | Résultat |
|---|---|---|---|
| 001 commande B par A | PASS (404) | 011 orderId deviné | PASS (isolation) |
| 002 commande B par A (fournisseur) | PASS (404 ×4) | 012 double callback | PASS (idempotent) |
| 003 escalade ADMIN | PASS (ignoré) | 013 webhook invalide | PASS (404/4xx) |
| 004 prix modifié | PASS (serveur) | 014 upload hostile | FIX→PASS (400) |
| 005 sur-stock | PASS (400) | 015 traversal | PASS (bloqué) |
| 006 course stock=1 | PASS (200+400) | 016 /admin direct | PASS (API 401) |
| 007 XSS message | PASS (échappé) | 017 /fournisseur direct | PASS (API 401) |
| 008 SQLi recherche | PASS (préparées) | 018 session expirée | PASS (401) |
| 009 supplierId modifié | PASS (400) | 019 pagination énorme | FIX→PASS (borné) |
| 010 userId modifié | PASS (isolation) | 020 JSON anormal | PASS (400 propre) |

## 9. Vulnérabilités — bilan

- VULN-001 (LOW) : upload refusé → 500 au lieu de 400. Corrigé, retesté.
- VULN-002 (MEDIUM) : pagination non bornée → 500 (OFFSET négatif, message
  générique, sans fuite SQL). Corrigé, retesté.
- CRITICAL : 0. HIGH : 0.
- Fausses alertes elucidées (artefacts du harnais, prouvés par retests ciblés
  avec état DB vérifié) : 500 initiaux sur avance-expédition-tiers,
  accept-offre-tiers, transition arrière, init paiement — tous dus à
  l'épuisement du stock de test (`orderId` indéfini) ; retests manuels : 400/403
  + état inchangé.

## 10. Totaux

111 tests sécurité/robustesse : 111 PASS, 0 FAIL, 0 BLOCKED.
Régression Phase 19 : 80/80 PASS. Unitaires : 62/62 (`mvn clean test`).
Intégrité DB après campagne : 0 orphelins, 0 stock négatif, 0 montant négatif.
