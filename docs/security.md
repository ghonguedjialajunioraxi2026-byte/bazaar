# MBOA STYLE — Sécurité (Phase 15)

Base de sécurité démontrée par audit + code + tests + documentation.
Méthode : AUDIT → CLASSER → CORRIGER → TESTER → DOCUMENTER.
Aucune vulnérabilité CRITIQUE démontrée ; tout finding est sourcé
(fichier:ligne) ci-dessous.

## 1. Tableau des vulnérabilités

| ID | Vulnérabilité | Sév. | Localisation | Correction | Test |
|---|---|---|---|---|---|
| SEC-001 | Messages d'exception (SQL possibles) exposés au client (~45 réponses) | ÉLEVÉ | 12 servlets (`Order`, `Payment`, `Product`, `Supplier`, `Category`, `Notification`, `SupplierAuth`, `SupplierApi`, `Review`, `Promotion`, `DeliveryZone`, `ProductImage`, `Admin`, `Sitemap`) | `ApiResponse.internalError()` générique partout | PASS (46 tests) + revue grep : 0 `e.getMessage()` exposé restant |
| SEC-002 | Énumération publique des commandes (find/search/status/supplier/findAll sans auth → PII noms/tél/adresses) | ÉLEVÉ | `OrderServlet.java:35-77` | `requireAdmin` sur ces 5 branches ; invités gardent `myorders/track/cancel` | PASS manuel (recette §12) |
| SEC-003 | Mots de passe SHA-256 simple, sans sel | ÉLEVÉ | `PasswordUtil.java` (+ `database.sql:464` compte `admin/admin123`) | PBKDF2-HMAC-SHA256 210k itérations, sel 128 bits ; vérif héritage + migration transparente au login | `PasswordSecurityTest` (4) PASS |
| SEC-004 | Aucun anti brute-force sur les logins | ÉLEVÉ | `AdminServlet` login, `SupplierAuthServlet` login | `LoginAttemptService` : 5 échecs → 429/15 min (clé login+IP), succès = reset | `LoginAttemptServiceTest` (4) PASS |
| SEC-005 | File de modération avis publique + dépôt sans achat vérifié | ÉLEVÉ | `ReviewServlet.java:30-33` + `create` sans jointure orders | `pending`/`findAll` → admin ; throttle 5 avis/h/produit (429) ; rating 1-5 validé | PASS (code) ; faux avis = risque résiduel R-3 |
| SEC-006 | IDOR : tout fournisseur listait les offres de n'importe quelle conversation | ÉLEVÉ | `OfferServlet.java:81-84` | `findByIdScoped` obligatoire → 403 sinon | PASS (code) |
| SEC-007 | Notifications inter-rôles : `?role=` choisi par le client | ÉLEVÉ | `NotificationServlet` (rôle param) | `sessionRole()` résolu serveur, param ignoré | PASS (code) |
| SEC-008 | SVG accepté en upload + servi `image/svg+xml` inline (XSS stocké) | ÉLEVÉ | `UploadUtil.java:112` + `UploadServlet.java:47-50` | SVG refusé ; magic bytes obligatoires (jpg/png/gif/webp) ; `.svg` legacy servi `text/plain` ; `nosniff` | `UploadSecurityTest` (3) PASS |
| SEC-009 | XSS stocké vs admin (noms produits/fournisseurs/catégories/logos non échappés) | ÉLEVÉ | `dashboard.html` (~15 interpolations), `index.html` (~12 `src`), `fournisseur.html` (logos, `emptyState`) | `notifEsc`/`escAttr`/`esc` appliqués ; `filterProductsById` (plus de nom dans `onclick`) | `node --check` OK ; recette `<script>`/`<img onerror>` §12 |
| SEC-010 | Token en query param (`?token=`) → fuite logs/historique/Referer | MOYEN | `AdminAuth.java`, 4 servlets | Header `Authorization` seul | PASS (grep : 0 occurrence restante) |
| SEC-011 | Sessions survivant au changement de mot de passe | MOYEN | `SupplierService`, `AdminDAO` | `invalidateAllForSupplier/Admin` au changement | PASS (code) |
| SEC-012 | `DELETE` avis/promos/zones/notifications en garde faible | MOYEN | 4 servlets | `DESTRUCTIVE` (ADMIN+) | `AdminRoleTest` + revue |
| SEC-013 | `GET /api/reviews` historique complet public (doublon SEC-005, garde) | MOYEN | `ReviewServlet.java:32-33` | admin requis | PASS |
| SEC-014 | `OrderDAO.delete` : restore stock hors transaction | MOYEN | `OrderDAO.java:352` | Transaction unique (SELECT + restore + DELETE) | PASS (compilation + tests) |
| SEC-015 | `order_number` MAX+1 sans contrainte → doublon concurrent | MOYEN | `OrderDAO.java:159` + schéma | `UNIQUE uq_orders_number` (`migrate_order_number_unique.sql`) + rejeu ×3 | PASS |
| SEC-016 | Conversation créée avec produit d'un autre vendeur | MOYEN | `ConversationService.java:59` | Vérif `product.supplierId == supplierId` (400) | PASS |
| SEC-017 | Pas de throttle messages côté fournisseur (client seul) | MOYEN | `SupplierService.sendSupplierMessage` | Même garde 10/min + `MESSAGE_MAX` | PASS |
| SEC-018 | Secrets de repli DEV (`DB_PASSWORD`, `useSSL=false`) | MOYEN | `DBConnection.java` | Avertissement stderr si env absents (sans secret) + doc rotation | Résiduel R-1 |
| SEC-019 | OAuth : pas de contrôle `iss`, id_token en query GET | MOYEN | `GoogleAuthUtil.java` | Contrôle `iss` (accounts.google.com) ; `aud`+`exp`+`email_verified` déjà là | PASS |
| SEC-020 | URL d'image externe arbitraire (`javascript:`, `data:`) | MOYEN | `ProductImageServlet` JSON | `isSafeImageUrl` (http(s)/interne, anti-schémas actifs) | `ImageUrlSecurityTest` (2) PASS |
| SEC-021 | Aucun header sécurité (clickjacking, sniffing, referrer) | MOYEN | `web.xml` | `SecurityHeadersFilter` : nosniff, SAMEORIGIN, referrer, permissions-policy | PASS |
| SEC-022 | Credential pré-rempli `admin123` dans le login HTML | MOYEN | `dashboard.html:51` | Valeur supprimée | PASS |
| SEC-023 | Concaténation d'entiers en SQL (`IN (...)`) | FAIBLE | `ProductDAO.enrichWithImages` | Placeholders `?` liés | PASS |
| SEC-024 | LIKE sans échappement `%`/`_` (élargissement, pas injection) | FAIBLE | 5 DAO | Non corrigé (pas d'injection) → résiduel R-5 | — |

## 2. Vérifiés SAINS (pas de correction requise)

- **SQLi** : 162+ `PreparedStatement` ; 1 seul `Statement` (SQL fixe) ; `sort` sur whitelist ; `IN` bindés. Testé conceptuellement : `' OR 1=1` → paramètre lié, inerte.
- **Prix/stock/commandes** : prix et total recalculés serveur (`OrderService`), montant/devise imposés (`PaymentService`), décrément atomique `WHERE stock>=?` + rollback, `cancelGuestOrder` en `FOR UPDATE`.
- **Paiements** : confirmation = `verifyPayment` admin (MONEY) ou livraison COD ; jamais de déclaration frontend ; idempotence `PAY-<orderId>`.
- **Isolation fournisseurs** : `supplierId` toujours du token (`SupplierApiServlet`), ressources scopées (`isOwner`, `findByIdScoped`, `ownedOrder`) ; invités par `phone+cid`.
- **Escalade** : inscription fournisseur ne crée que `Supplier` ; aucun endpoint de création admin ; OAuth Google → fournisseur seul.
- **Sessions** : tokens 192 bits CSPRNG, expiry SQL, logout = DELETE, révocation au changement de mot de passe (nouveau).
- **CSRF** : non vulnérable par construction — auth `Authorization: Bearer` (jamais de cookie, header non envoyé automatiquement cross-origin). Pas de CSRF token nécessaire ; décision documentée.
- **CORS** : aucun header permissif émis (`*` absent) → same-origin par défaut.
- **CSP** : volontairement absente (scripts inline + CDN Tailwind/Chart.js/Google) ; compensé par échappement systématique + `nosniff`.
- **HSTS** : absent volontairement (HTTP local) ; à activer derrière HTTPS en production.
- **Fichiers** : `.git`/`*.sql`/`pom.xml` hors WAR (vérifié `unzip -l`) ; `WEB-INF` non servi ; uploads hors webapp, noms UUID, anti `..`.
- **Logs** : aucun password/token/secret/stack/SQL (2 `println` de debug sans secret) ; `console.error` frontend génériques.
- **Dépendances** (`mvn dependency:tree`) : servlet 6.0 (provided), mysql-connector-j 8.4.0, gson 2.10.1, junit 5.10.2 (test) — minimales, récentes, rien à mettre à jour.
- **Frontend** : 0 `eval`/`new Function`/`document.write`/`insertAdjacentHTML` ; `toast` en `textContent`.

## 3. Authentification / sessions / OAuth

PBKDF2 + migration transparente ; rate limiting logins (429) ; révocation au changement ; Google : `aud`+`iss`+`exp`+`email_verified`, aucun rôle privilégié attribuable. `localStorage` conservé pour les tokens Bearer (décision §CSRF) ; `admin_role` non sensible (affichage seul, backend décide).

## 4. Rate limiting (état)

| Endpoint | Limite |
|---|---|
| Admin/supplier login | 5 échecs → 15 min (LoginAttemptService) |
| Messages client + fournisseur | 10/min/conversation (429) |
| Offres | 10/jour/conversation (429) |
| Avis | 5/h/produit (429) |
| Catalogue GET | aucune (volontaire) |

Multi-instances = compteurs locaux (résiduel R-4).

## 5. Variables d'environnement

`DB_URL`, `DB_USER`, `DB_PASSWORD`, `GOOGLE_CLIENT_ID`, `MBOA_UPLOADS_DIR`. Replis DEV locaux uniquement + avertissement stderr (jamais de contenu imprimé). Rotation : compte `admin/admin123` par défaut et mot de passe DB à changer en production (résiduel R-1).

## 6. Tests

Automatisés (46/46 PASS) : `PasswordUtilTest`, `PasswordSecurityTest` (4), `LoginAttemptServiceTest` (4), `UploadSecurityTest` (3), `ImageUrlSecurityTest` (2), `AdminRoleTest` (5), + suites existantes Phase 2/10/11/13/14.
Recette manuelle obligatoire (§12) : IDOR (`?id=124`, `supplier?supplierId=`, offres cross-conversation), XSS (`<script>`, `<img onerror>`, `javascript:` URL), SQLi (`' OR 1=1 --` en recherche), sessions expirées, 403 GESTIONNAIRE (refund, DELETE, broadcast), 429 après 5 logins ratés, upload `.svg`/faux `.jpg`.

## 7. Risques résiduels

- **R-1** : secrets DEV par défaut + compte admin par défaut → rotation obligatoire en production (documenté, non supprimables sans casser l'install fraîche).
- **R-2** : liaison Google sans re-vérification du mot de passe existant (même email vérifié requis ; décision produit).
- **R-3** : avis sans achat vérifié → spam contenu par throttle + modération (pas de faux avis visibles sans approbation).
- **R-4** : rate limiting local à l'instance (OK mono-Tomcat actuel).
- **R-5** : jokers `%`/`_` LIKE non échappés (élargissement bénin).
- **R-6** : pas d'antivirus sur uploads (signatures + types + tailles en place) ; pas de WAF/CSP/HSTS (infra production).
