# MBOA STYLE — Admin Control Center (Phase 14)

Centre de pilotage de la marketplace. Réutilise l'architecture existante
(Servlet → Service → DAO → MySQL, `ApiResponse`/`AppException`/`ErrorCodes`,
sessions admin) ; aucune table `_admin` dupliquée, aucune donnée inventée.

## 1. Architecture admin

```
dashboard.html (pages + JS par domaine)
   ↓  fetch /api/admin/* (Bearer token, rôle résolu serveur)
AdminServlet (GET lecture, POST actions) + servlets métier
   ↓
AdminControlService (supervision) / services métier
   ↓
DAO (+ requêtes enrichies JOIN, 1 requête par liste, jamais N+1)
   ↓
MySQL (tables métier existantes + activity_logs)
```

Fichiers créés : `util/AdminRole.java`, `service/AdminControlService.java`,
`docs/admin-space.md`, test `util/AdminRoleTest.java`.

## 2. Routes (`/api/admin/*`, session requise)

| Route | Accès | Source |
|---|---|---|
| `/dashboard`, `/stats`, `/finance`, `/logs` | tous rôles | existant |
| `/me` | tous rôles | session → `admins` |
| `/overview` | tous rôles | COUNT ciblés (voir §4) |
| `/payments?status=&page=&limit=` | tous rôles | `payments ⨝ orders` |
| `/shipments?filter=&limit=` | tous rôles | `shipments ⨝ orders` |
| `/conversations?page=&limit=` | tous rôles | conversations + boutique (contenus exclus, tél. masqués) |
| `/offers/pending?page=&limit=` | tous rôles | `offers ⨝ conversations/suppliers/products` |
| `/reviews?status=` | tous rôles | `reviews` (plafond 200) |
| `/audit?action=&page=&limit=` | tous rôles | `activity_logs ⨝ admins` |
| `/password` (POST) | tous rôles | vérifie l'actuel, min 8, audité |
| `/orders/refund`, `/finance/rebuild`, `/monetization/rules*` | ADMIN+ | MONEY |
| `/product/validate` | tous rôles | modération opérationnelle, auditée |

Verrouillés aussi (hors AdminServlet) : vérification/échec paiements
(MONEY), suppressions commandes/fournisseurs/produits/catégories/promotions
(DESTRUCTIVE), diffusion notifications (BROADCAST). Pas d'endpoint de
création de comptes admin (ROLE_MGMT réservé SUPER_ADMIN, non exposé).

## 3. Rôles et permissions (matrice réelle)

| Capacité | GESTIONNAIRE | ADMIN | SUPER_ADMIN |
|---|---|---|---|
| Lecture, statuts commandes/expéditions, validation produits, modération avis, notifications | ✓ | ✓ | ✓ |
| Argent : remboursement, vérif/échec paiements, règles, rebuild | ✗ (403) | ✓ | ✓ |
| Suppresssions (commande, fournisseur, produit, catégorie, promo) | ✗ (403) | ✓ | ✓ |
| Diffusion notifications | ✗ (403) | ✓ | ✓ |
| Comptes admin | ✗ | ✗ | réservé (non exposé) |

Enforcement : `AdminRole.requireAction()` (403 sinon), rôle résolu
serveur (`AdminSessionDAO` → `AdminDAO.findById`). Le frontend masque les
boutons (indicatif) ; seul le backend décide. Identité affichée via `/me`.

## 4. KPIs — sources exactes

Vue générale = `/dashboard` existant + `/overview` :
`distinctClients` = `COUNT(DISTINCT phone)` orders (invités, pas de comptes) ;
`unreadMessages`/`conversations` = messages CLIENT non lus ;
`pendingOffers` = offers PENDING ; `failed/pending/refundedPayments` =
statuts payments ; `activeShipments`/`incidents` = statuts shipments ;
`pendingProducts` = products PENDING ; `pendingReviews` ; `pendingPayouts` =
supplier_payments PENDING/PROCESSING ; `unreadNotifications`.
Finances : `MonetizationService.platformOverview()` (ledger, GMV ≠ revenus
MBOA — libellé conservé). Donnée absente = `—`, jamais 0 déguisé.

## 5. Pages du Control Center

Vue générale · Commandes (inchangée + confirmations Design System) ·
Paiements (filtres, statuts réels EN_ATTENTE/REUSSIE/ECHOUEE/ANNULEE) ·
Expéditions (en cours/incidents/toutes, statuts Phase 12) · Fournisseurs
(suspension confirmée + auditée, suppression ADMIN+) · Messages
(supervision sans contenus) · Notifications (Phase 13) · Avis (file
PENDING, approbation/suppression auditées) · Catégories & Produits ·
Promotions · Statistiques + finance · Activité (= commandes réelles ;
**aucune mesure d'audience : les faux « 247 visites +12 % » ont été
supprimés**) · Journal d'audit (filtre action + pagination) · Paramètres
(identité réelle, mot de passe, note modèle invités).

## 6. Utilisateurs

Pas de table utilisateurs : acheteurs = invités (téléphone). Pas de
table inventée. Pilotage via Commandes, Messages, KPI clients uniques.

## 7. Modération

Avis : PENDING → approuvé (visible boutique) / supprimé, avec
`REVIEW_APPROVED`/`REVIEW_DELETED` dans l'audit. Produits : validation
inchangée + audit existant. Messages : supervision seule (pas de lecture
des contenus, pas de modification d'historique). Signalements : aucune
table — non créé (dette §10).

## 8. Audit

`activity_logs(admin_id, action, entity_type, entity_id, details, ip,
created_at)` + username joint. Nouvelles actions : REVIEW_*,
SUPPLIER_ACTIVATED/SUSPENDED, ADMIN_PASSWORD_CHANGED (+ existants :
LOGIN/LOGOUT, PRODUCT_*, ORDER_REFUNDED, FINANCE_REBUILT,
MONETIZATION_RULE_*). Jamais de mot de passe/token/secret. Page Audit :
filtre action, pagination 20, export CSV : non (dette).

## 9. Actions dangereuses

`confirmAction()` (modale Design System, jamais `confirm()`/`prompt()`
natifs) pour : vérifier/rejeter paiement, annuler commande,
suspendre/supprimer fournisseur, suppressions catalogue/avis/promo.
Transactions : celles des services existants réutilisées
(commande/ledger/expédition).

## 10. Dettes et limites

1. Pas de table `reports`/signalements (modération réactive seule).
2. Pas d'exports CSV (limite+autorisation+log à prévoir).
3. Pas de gestion des comptes admin en UI (réservé SUPER_ADMIN, SQL direct).
4. Audit : pas de purge/rétention automatisée.
5. `markRefunded(orderId, true)` silencieux si déjà traité (ok métier).
6. Tests HTTP/DB : manuels (requêtes ci-dessous) ; unitaires : matrice
   `AdminRole` (5 tests). Sécurité approfondie = Phase 15.

```bash
# 401 sans token
curl /api/admin/overview # → 401
# 403 gestionnaire sur MONEY (token gestionnaire)
curl -X POST /api/admin/orders/refund -H "Authorization: Bearer G" \
  -d '{"id":1}' # → 403
# adminId/role falsifiés ignorés (session seule)
curl "/api/admin/payments?status=REUSSIE&page=1&limit=20" -H "Authorization: Bearer A"
```

## 11. Régressions

`mvn clean test` 33/33, `mvn package` WAR OK. Pages existantes
(commandes, catégories, promos, fournisseurs, notifs, stats, settings)
conservées et enrichies ; API existantes inchangées (réponses additives).
