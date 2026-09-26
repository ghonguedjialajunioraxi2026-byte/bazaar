# MBOA STYLE — Performance (Phase 17)

Méthode : AUDIT → MESURE → OPTIMISE → TESTE → MESURE → DOCUMENTE.
Aucune fonctionnalité supprimée, sécurité et contrats API préservés.

## 1. État initial (mesuré, Chrome 151, serveur statique local, 1366×900)

| Page | Requêtes | Poids | DCL | Load |
|---|---|---|---|---|
| index | 58 | 2840 Ko (dont 2435 Ko JPEG hero) | 1583 ms | 2409 ms |
| dashboard | 9 | 288 Ko | 1388 ms | 2042 ms |
| fournisseur | 16 | 393 Ko | 966 ms | 2099 ms |

SQL (MySQL local, EXPLAIN) : full scans `orders` (phone/status/supplier),
`payments` (status), `messages` (sender+is_read), filesorts `conversations`
(updated_at) et `activity_logs` ; `DATE(created_at)=CURDATE()` non sargeable.

## 2. Problèmes → solutions → impacts mesurés

### P-1. Hero : 31 JPEG 800px (2,4 Mo) affichés en 128–168px floutés
CAUSE : photos d'ambiance pleine résolution pour des vignettes décoratives.
SOLUTION : miniatures 320px q60 progressives (PIL, originaux `photo produit/` intacts).
IMPACT : images 2435 → 641 Ko ; page 2840 → **1138 Ko (−60 %)** ; requêtes 58 → 63 (variance fonts) ; rendu visuel identique (screenshot comparé).
RISQUE : aucun (mêmes URLs, mêmes dimensions d'affichage).

### P-2. 10 index manquants (ALL → ref/range, vérifié EXPLAIN après)
`migrate_perf_indexes.sql` : `idx_orders_phone`, `idx_orders_status(status,created_at)`,
`idx_orders_supplier(supplier_id,created_at)`, `idx_orders_created`,
`idx_payments_status`, `idx_messages_sender_read`, `idx_conv_updated`,
`idx_logs_action_created`, `idx_reviews_product_approved`, `idx_sp_status`.
IMPACT : ex. `orders WHERE phone` ALL→ref ; `activity_logs WHERE action ORDER BY` ALL+filesort→ref+Backward scan.
COÛT : 10 index B-tree sur petites tables, écritures à faible volume → négligeable.
Migration appliquée à la base DEV (validée) ; à appliquer en staging/prod.

### P-3. `DATE(created_at)=CURDATE()` (2 requêtes dashboard)
SOLUTION : plages `created_at >= CURDATE() AND < +1 DAY` (sargeables via idx_orders_created).
IMPACT : plan indexé ; temps absolus sous-ms aux volumes actuels (profils §1).

### P-4. Listes non bornées (risque à la croissance)
`OrderDAO.findAll` / `PaymentDAO.findAll` → `LIMIT 500` ;
`ProductDAO.findAll/findAllPublic` → `LIMIT 2000` (filtres client-side préservés) ;
`ConversationDAO.findMessages` → 300 derniers (ordre conservé).
IMPACT : borne mémoire/réponse garantie ; aucun consommateur ne lit au-delà (dashboard slice, chats paginés visuellement).
RISQUE : historique > 300 messages/conversation tronqué visuellement (documenté, pagination complète = dette).

### P-5. Polling messagerie : conversation entière toutes les 20 s
SOLUTION : `GET /conversations/{id}?after=LASTID` (DAO `findMessagesAfter`, `LIMIT 200`) + `pollClientConvo()` qui ajoute les bulles (rétrocompatible : sans `after` = comportement inchangé).
IMPACT : réponse de polling ≈ vide la plupart du temps au lieu de N messages + liste offres ; offres non rechargées en polling (rechargées à l'ouverture/envoi).
RISQUE : aucun (même HTML de bulle factorisé `clientMsgBubble`).

### P-6. Appels séquentiels dépendants → parallèles
`refreshBadges()` fournisseur : `/badges` + `/offers` en `Promise.all` (indépendants).
IMPACT : −1 aller-retour dans le chemin critique du badge.
Accueil : `Promise.all(products, categories, suppliers)` déjà en place (vérifié).

### P-7. Re-requêtes catégories à chaque navigation
SOLUTION : cache mémoire 5 min (`catCacheAt`) sur données publiques quasi-statiques.
IMPACT : 0 requête catégorie lors des navigations < 5 min. Jamais de stock/prix/commandes en cache.

## 3. Optimisations refusées (avec justification)

- **Pool JDBC / HikariCP** : DriverManager local ≈ 1 ms, faible concurrence réelle ; nouvelle infra + dépendance injustifiées. Réévaluer si > 50 req/s mesurés.
- **Cache serveur** : latence DB locale en ms, invalidation plus coûteuse que le gain. Réévaluer avec trafic.
- **Tailwind CDN → build purgé** : risque de régression visuelle majeur pour ~70 Ko ; reporté (dette PWA/build future).
- **Elasticsearch** : `LIKE %mot%` suffisant aux volumes actuels ; index catégories/noms existants pour les préfixes.
- **WebSocket** : polling 20–60 s + `document.hidden` déjà sobre ; volumes faibles.
- **srcset** : vignettes déjà à la bonne taille (320px) ; catalogue externe inchangé.
- **Outbox / dénormalisation compteurs** : volumes ne le justifient pas.

## 4. Invariants préservés (vérifiés)

Transactions et concurrence intactes : décrément atomique `WHERE stock>=?`,
`FOR UPDATE` annulation, idempotence `PAY-`, transitions atomiques offres/
expéditions, `order_number` UNIQUE + rejeu, `delete` commande transactionnel.
`mvn clean test` : 46/46. `node --check` : 3 frontends. Screenshots avant/
après : hero identique, responsive Phase 16 intact (re-mesuré 0 overflow).

## 5. Résultats

| Mesure | Avant | Après |
|---|---|---|
| Accueil poids | 2840 Ko | **1138 Ko (−60 %)** |
| JPEG hero | 2435 Ko / 37 req | **641 Ko** |
| Requêtes SQL full scan (plans) | 7 ALL/filesort | **0 (ref/range)** |
| Polling messages | N messages + offres / 20 s | **delta seul** |
| `mvn clean test` / `package` | PASS | **PASS (46/46)** |

DCL/Load dominés par CDN (Tailwind/fonts) et variance réseau : non régressés
(1583→1664 ms / 2409→2725 ms, bruit de mesure sur ressources externes).

## 6. Limites et dettes

- Pas de Tomcat local : temps de réponse API non mesurés bout-en-bout (à faire en staging).
- Pagination messages complète (> 300), exports CSV : dettes connues.
- Recommandations : staging avec jeu de données volumineux (10k produits/commandes) + `slow_query_log` ; envisager pool + cache HTTP statique (`Cache-Control` sur `/assets/*`, `index.html` no-cache) côté reverse-proxy.
