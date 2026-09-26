# MBOA STYLE — Notifications (Phase 13)

Système centralisé, idempotent et sécurisé. Aucun second système :
la table `notifications` historique est étendue (migration additive v3),
jamais remplacée.

## 1. Architecture

```
ÉVÉNEMENT MÉTIER (serveur uniquement)
       ↓
NotificationService.notify(...)   ← destinataire déduit serveur, jamais frontend
       ↓
NotificationDAO (createFull / findIdByDedup / scoped read)
       ↓
MySQL (notifications + notification_preferences)
       ↓
UI : dashboard (admin) · fournisseur.html · index.html (timeline invité)
```

Fichiers :

- `service/NotificationType.java` — nomenclature (codes, catégories, priorités, seuils, sanitization)
- `service/NotificationService.java` — point d'entrée unique (idempotence, préférences)
- `dao/NotificationDAO.java` — persistence + scopes anti-IDOR + préférences
- `controller/NotificationServlet.java` — centre admin (`/api/notifications`)
- `controller/SupplierApiServlet.java` — `/api/supplier/notifications*`
- `controller/GuestNotificationServlet.java` — `/api/guest-notifications` (timeline dérivée)
- `database/migrate_notifications_v3.sql` — migration additive

## 2. Types (event codes réellement émis)

ORDER_CREATED, ORDER_CONFIRMED, ORDER_CANCELLED ·
PAYMENT_PENDING, PAYMENT_SUCCEEDED, PAYMENT_FAILED, PAYMENT_REFUNDED ·
DELIVERY_PREPARING, DELIVERY_SHIPPED, DELIVERY_IN_TRANSIT,
DELIVERY_OUT_FOR_DELIVERY, DELIVERY_DELIVERED, DELIVERY_FAILED ·
MESSAGE_RECEIVED · OFFER_RECEIVED, OFFER_COUNTERED (via parent),
OFFER_ACCEPTED, OFFER_REJECTED · PRODUCT_LOW_STOCK, PRODUCT_OUT_OF_STOCK,
PRODUCT_SUBMITTED, PRODUCT_APPROVED, PRODUCT_REJECTED ·
ACCOUNT_SECURITY, PASSWORD_CHANGED · SYSTEM, MARKETING.

Inconnu → `SYSTEM` (jamais d'échec, jamais de fiction).

## 3. Catégories

ORDER, PAYMENT, DELIVERY, MESSAGE, NEGOTIATION, PRODUCT, SECURITY,
SYSTEM, MARKETING (+ STOCK/SUPPLIER historiques conservés).
La catégorie = `type` historique (compat) ; `category` v3 suit l'event code.

## 4. Priorités

LOW < NORMAL < HIGH < CRITICAL (`URGENT` historique = CRITICAL).
Défaut par event code (ex. PASSWORD_CHANGED → CRITICAL, MARKETING → LOW).

## 5. Création

`NotificationService.notify(role, userId, eventCode, title, message,
entityType, entityId, actionUrl, dedupKey)`.
Textes clampés (200/2000), URL validée (relative `/...` uniquement),
préférences vérifiées. Retourne l'id créé **ou existant** (idempotence).

## 6. Lecture

Admin : `GET /api/notifications?role=R&type=&status=&q=&page=&limit=`
(10/page par défaut, max 50). Fournisseur :
`GET /api/supplier/notifications?type=&status=&page=&limit=` (+ `total`).
Tri `created_at DESC`. Invité : timeline dérivée (voir §17).

## 7. Compteur

- Admin : `GET /api/notifications?action=unread-count&role=R` → `{"count":N}`
- Fournisseur : `GET /api/supplier/notifications/unread-count`
- Invité : champ `unread` de `/api/guest-notifications`
Requêtes `COUNT(*)` sur index, jamais de `99+` codé en dur (plafond d'affichage seul).

## 8. Déduplication

Clé logique `dedup_key` UNIQUE, ex. `ORDER:12:PAYMENT_SUCCEEDED`,
`SHIPMENT:3:DELIVERY_DELIVERED`, `CONV:5:MSG:42`, `PRODUCT:9:LOW:SUP4`.
Pré-contrôle + rattrapage `Duplicate entry` (course concurrente).

## 9. Idempotence

Webhook ×3 → 1 notification (test `duplicatePaymentWebhookCreatesSingleNotification`).
`createShipment` existant → retourné sans doublon (inchangé Phase 12).

## 10. Préférences

Table `notification_preferences` (rôle, user, catégorie, enabled).
`GET/PUT ...?action=preferences` (admin), `GET/PUT /api/supplier/notifications/preferences`.
`SECURITY` non désactivable (serveur + UI). Défaut = activé.

## 11. Canaux

Phase 13 = `IN_APP` uniquement. Email/push/SMS non créés ;
`metadata` TEXT prêt pour routage futur. Panne email (inexistante) sans
impact : la notification interne ne dépend d'aucun canal externe.

## 12. Sécurité

- Destinataire serveur uniquement ; `?userId=` ignoré (rôle = session).
- IDOR corrigé : `markReadScoped`/`markUnreadScoped`/`deleteScoped`
  (`WHERE id=? AND target_role=?`) → 403 sinon. Avant : `markRead(id)` et
  `delete(id)` non scopés (faille).
- `entityId` jamais preuve d'autorisation (suivi/order revérifiés).
- `POST /api/notifications` réservé admin (création transactionnelle
  interdite au frontend) ; diffusion admin auditée (`activity_logs` existants
  pour validate ; dette : log générique du compose — voir §20).
- Rate limiting : garde-fou existants réutilisés (10 msg/min, 10 offres/jour,
  quantités ≤ 99) ; pas de rate-limit dédié notifications (dette §20).

## 13. XSS

Rendu via `notifEsc`/`esc`/`escHTML` (jamais d'`innerHTML` brut),
`textContent` pour les toasts fournisseur. Contenus longs clampés,
URLs dangereuses rejetées (`sanitizeActionUrl` testé).
Test manuel : `<script>alert(1)</script>` en nom produit/message/offre → inerte.

## 14. Pagination

`page`/`limit` partout (max 50). Compteur séparé et léger. Fournisseur :
ancien `LIMIT 60` conservé en repli via endpoint paginé.

## 15. Performance

Index : `idx_notif_role_read_date` (v2), `idx_notif_user_read_date`,
`idx_notif_category`, `idx_notif_entity`, `uq_notif_dedup` (v3).
Polling : 30 s admin, 45 s fournisseur, 60 s invité, pause si
`document.hidden`. Pas de chargement massif (jamais 1000 lignes).

## 16. Temps réel — Niveau 1 (polling)

Choix justifié : Tomcat classique sans infra WS/SSE, messagerie déjà en
polling (20 s), volumes faibles, besoin non critique. Pas de second système
concurrent. Évolution SSE documentée si volume ×100 (voir §20).

## 17. Notifications transactionnelles vs invités

Clients = invités sans comptes : **aucune ligne `CLIENT` persistée**
(un broadcast fuirait entre invités). `/api/guest-notifications`
(téléphone + cid, comme suivi/messagerie) dérive en direct :
statuts commandes, paiements, expéditions + tracking, messages non lus,
contre-offres. Données 100 % réelles, zéro fiction.

## 18. Marketing

Séparé : catégorie `MARKETING`, priorité LOW, opt-out indépendant,
jamais de campagne auto. Consentement : opt-in par défaut activé mais
désactivable ; aucune campagne émise en Phase 13.

## 19. Limitations (dette explicite)

1. Rate-limit dédié aux notifications (actuellement : gardes métier seuls).
2. Log `activity_logs` du compose admin (validate déjà loggé).
3. Rétention : politique proposée — ordinaires 12 mois, financières/sécurité
   36 mois (à valider métier/réglementaire) ; purge actuelle = manuelle
   (`clearRead`, lues seules).
4. SSE si montée en charge ; groupage (15 cmd/min) non implémenté —
   bruit fournisseur faible aujourd'hui.
5. Tests IDOR/XSS DB-dépendants : manuels (requêtes §suivantes), unitaires
   en place pour logique pure.

Recette IDOR manuelle :

```bash
# A = token admin, lire notif d'un autre rôle → 403 attendu
curl -X PUT /api/notifications -H "Authorization: Bearer A" \
  -d '{"action":"read","id":<id_supplier>}' # → 403 NOTIFICATION_ACCESS_DENIED
```

## 20. Évolutions futures

Transactional Outbox (si volumes), SSE, groupage (`group_key`),
préférences par canal (EMAIL/PUSH quand infra réelle), campagnes marketing
avec consentement, purge automatique selon rétention validée.
