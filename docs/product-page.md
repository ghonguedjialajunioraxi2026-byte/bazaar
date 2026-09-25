# MBOA STYLE — Fiche produit (Phase 5)

La fiche est la modale `#product-modal` (`openProduct(id)`), URL partageable
`#/produit/{id}` (init + `hashchange`, titre synchro, fermeture → `#/`).

## Structure
Breadcrumb (Accueil/Boutique/chemin catégorie, cliquable) → badge →
marque → nom → note (+compte) → prix (+ancien, −%) → stock → description →
caractéristiques (marque/référence/catégorie/vendeur **si renseignés**) →
vendeur (négocier/contacter WhatsApp) → couleurs/tailles → quantité
→ Ajouter/Commander → favoris/partage → **avis** → **similaires**.

## Variantes (modèle réel audité)
`products.sizes` / `products.colors` (JSON), **stock GLOBAL uniquement**.
Sélection visuelle + `aria-pressed`, valeurs via `data-val` (anti-XSS).
Pas de stock par variante : documenté ici, aucun seuil inventé.
Quantité bornée à `min(10, stock)` ; serveur (`OrderDAO`, décrément
atomique `WHERE stock >= ?`, seuil bas ≤ 5) = source de vérité.

## Galerie / avis / similaires
Miniatures `<button>` clavier + dots mobile + lightbox fullscreen ;
`alt` descriptifs ; fallback `IMG_FALLBACK`. Avis = approuvés uniquement
(`approved = 1`), formulaire → `POST /api/reviews` (modération, 400/500
gérés). Similaires = même catégorie (max 4), jamais de faux.

## Endpoints
`GET /api/products` (fiche via cache `PRODUCTS`), `GET /api/reviews?action=product&productId=`,
`POST /api/reviews`. Aucun contrat modifié, aucun schéma modifié.
