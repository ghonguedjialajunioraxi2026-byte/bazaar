# MBOA STYLE — Panier (Phase 6)

## Modèle
Hybride : `localStorage` (`camshop_cart`, `camshop_favs`) pour l'UX
instantanée + **serveur source de vérité** à la commande.
Ligne : `{id, qty, color, size, name, price, image, brand, supplierId,
supplierName, supplierWhatsapp, slug}` (+ `outOfStock` transient).
Invité = connecté : même mécanisme (pas de sync compte, pas de backend).

## Multi-fournisseurs
`groupCartBySupplier()` (ordre d'ajout préservé, index d'origine pour
+/- et suppression). Tiroir + récap groupés avec sous-totaux par vendeur.

## Quantités / stock / prix
- UI : 1–`min(10, stock)`, toast si plafond stock ; `-`/qty/`NaN` impossible.
- Ouverture tiroir : `reconcileCart()` — produit disparu → retiré+signalé ;
  prix modifié → mis à jour+signalé ; qty > stock → ajustée+signalée ;
  stock 0 → badge rupture, checkout bloqué avec message.
- Serveur (`OrderService` + `OrderDAO`) : prix **rechargés DB** (valeurs
  navigateur ignorées), produit absent/inactif refusé (404/400), stock
  insuffisant refusé (400), décrément atomique `WHERE stock >= ?` avec
  contrôle du comptage (anti-survente). Total = prix DB + frais serveur.

## Suppression / vide / persistance
Suppression ciblée + toast **Annuler** (6 s) ; vidage en 2 temps
(confirmation) ; vide pro + CTA ; `localStorage` (refresh, navigation) ;
synchro multi-onglets via événement `storage` (panier, favoris, récents) ;
corrompu → conservé.

## Sécurité / a11y / API
Champs fournisseur échappés (`escHTML`) dans tous les rendus ; pas de
`Statement` ; validation `RequestValidator` + `ApiResponse`.
`aria-label` ±, `aria-live` quantité. Aucun endpoint modifié, aucun
champ renommé. Limite : pas de réservation de stock au panier.
