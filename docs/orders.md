# MBOA STYLE — Commandes internes (Phase 7)

## Modèle
`orders` (numéro `CMD-AAAA-NNNNNN` unique, identité invitée
`fullname/phone`, adresse snapshot, `supplier_id`, statut ENUM,
`total` serveur) + `order_items` (`product_id`, **`product_name`
snapshot Phase 7, `size/color`, `qty`, `unit_price` snapshot).
Client = invité identifié par **téléphone** (pas de comptes clients ;
documenté comme facteur faible, suffisant pour ce périmètre).

## Multi-fournisseurs
Checkout : **1 commande / fournisseur** (`supplier_id` positionné).
Historique/détail affichent le vendeur ; sous-total = somme des lignes.

## Création (transactionnelle)
`OrderService.createOrder` : re-price DB, produit actif, stock suffisant,
total = prix DB + frais zone. `OrderDAO.create` : INSERT commande +
lignes + décrément atomique `WHERE stock >= ?` (compte contrôlé) +
`COMMIT`, `ROLLBACK` sinon. Jamais d'état partiel.

## Statuts (`OrderStatus`)
`EN_ATTENTE → CONFIRMEE → EN_PREPARATION → EN_LIVRAISON → LIVREE`,
`EN_ATTENTE|CONFIRMEE → ANNULEE`. Transitions validées côté admin
(`OrderService.updateStatus`) ET fournisseur
(`updateOrderStatusForSupplier`) : 400 `INVALID_ORDER_STATUS` sinon.
Annulation invitée : `EN_ATTENTE` uniquement, stock restauré
(`cancelGuestOrder`, `SELECT … FOR UPDATE`).

## Parcours invité
Confirmation (numéros + Voir ma commande + contacts vendeur optionnels,
WhatsApp NON moteur) → suivi `#/commande/{numero}` (numéro + téléphone
mémorisé) → historique `#` via `Mes commandes` → détail (lignes snapshot,
timeline, annulation si éligible).

## API
`GET action=myorders|track` (public, téléphone requis),
`POST action=cancel` (public, EN_ATTENTE uniquement),
CRUD existants inchangés. Erreurs : `ORDER_NOT_FOUND` 404,
`ORDER_ACCESS_DENIED`-like 404 (pas de fuite), `VALIDATION_ERROR` 400.
Recherche publique bornée au **numéro** (anti-énumération PII).

## Limites assumées
Téléphone = preuve faible (pas de compte) ; pas de remboursement
(annulation = stock + statut) ; statuts sans horodatage par étape.
