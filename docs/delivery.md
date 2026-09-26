# MBOA STYLE — Livraison + suivi (Phase 12, interne)

## Principe honnête
Aucun transporteur externe (pas d'API DHL/FedEx/UPS/locales) :
`carrier` vaut `INTERNAL` par défaut, architecture extensible
(`DeliveryProvider` futur). Pas de GPS, pas de dates estimées inventées,
pas de webhooks externes.

## Modèle
`shipments` (1 commande mono-fournisseur = 1 expédition,
`tracking_number` unique `MBOA-TRK-XXXXXXXX`, statuts, `failure_reason`,
`shipped_at/delivered_at`) + `shipment_events` immuables (INSERT seul :
statut, libellé, détail, source, horodatage). Adresse = snapshot de la
commande (aucune table adresse). Frais = `delivery_zones` existants,
calculés serveur (jamais du navigateur).

## Machine (`ShipmentStatus`)
PREPARING → READY_FOR_PICKUP → PICKED_UP → IN_TRANSIT →
OUT_FOR_DELIVERY → DELIVERED ; FAILED/RETURNED depuis
PICKED_UP/IN_TRANSIT/OUT_FOR_DELIVERY ; CANCELLED depuis
PREPARING/READY_FOR_PICKUP. Transitions atomiques
(`WHERE status=`, 409 sinon). Livrée → commande LIVREE (chaîne
comptabilisation + COD existante) ; commande ANNULEE → expédition
non terminale annulée.

## Rôles / sécurité
Fournisseur : ses commandes uniquement (vérifié via commande).
Invité : suivi par numéro + téléphone, confirmation de réception
(OUT_FOR_DELIVERY uniquement). Admin : supervision + avance.
IDOR testés (403/404 sans fuite).

## Notifications / multi-fournisseur
Notifs fournisseur existantes (création, échec/retour, livraison).
Multi-fournisseurs = multi-commandes = multi-expéditions indépendantes.
Pas d'état global PARTIALLY (non justifié).

## API
`POST /api/shipments`, `PATCH /:id/status`, `GET /order/:id`,
`GET /track`, `GET /by-order/:n`, `POST /confirm-received`,
`GET /active` (admin). Voir `docs/api.md`.

## Limites
Pas de transporteurs, GPS, dates estimées, retours logistiques avancés,
webhooks, pagination expéditions (limite 100/200).
