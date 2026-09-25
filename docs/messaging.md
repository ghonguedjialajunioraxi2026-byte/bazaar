# MBOA STYLE — Messagerie + négociation (Phase 8)

## Identité
Pas de comptes clients : invité = **téléphone + clé navigateur**
(`ms_cid`, `client_identifier`). Chaque accès vérifie les deux
(anti-IDOR → 403/404 sans fuite). Fournisseur = token Bearer 12h.
Limite documentée : le téléphone est un facteur faible.

## Tables
`conversations` (supplier, client_phone/cid/name, product_id, order_id,
subject, OPEN/CLOSED) + `messages` (sender CLIENT/SUPPLIER, is_read)
préexistantes et réutilisées. **Nouveau** : `offers`
(conversation, produit, qty, taille/couleur, `catalog_price`,
`unit_price`, `created_by`, statuts PENDING/ACCEPTED/REJECTED/
COUNTERED/CONSUMED/CANCELLED, `parent_offer_id`) + `order_items.offer_id`.

## Négociation
Offre (server check : produit du vendeur, qty ≤ stock, prix > 0,
≤10/jour/conversation) → contre-offre = nouvelle ligne + ancienne
COUNTERED → accept/refus atomique (`WHERE status='PENDING'`, 409 sinon)
→ acceptée + "Ajouter au panier" → commande via `offerId`
(revérification + prix offre + CONSUMED atomique, anti double-usage).
Prix catalogue jamais modifié. Stock global (pas de stock/variante).

## Temps réel / notifs / anti-spam
Polling 20 s (badge + chat si visible, pause onglet caché) ; pas de
WebSocket (documenté futur). Notifs fournisseur créées (message/offre) ;
invités via polling. Anti-spam : réutilisation conversation, longueurs
bornées, 10 msg/min, 10 offres/jour.

## Sécurité
IDOR (403), spoofing sender/supplier impossible (résolus serveur),
prix/qty validés, XSS échappée (`escHTML`, jamais `innerHTML` brut),
erreurs `ApiResponse`. Concurrence : transitions conditionnelles.

## API
`GET|POST /api/conversations…`, `GET /api/offers`,
`POST /api/offers`, `PATCH /api/offers/{id}/accept|reject|cancel`,
`GET /api/supplier/conversations/{id}` (+offres). Voir `docs/api.md`.
