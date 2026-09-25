# MBOA STYLE — Catalogue / catégories / recherche (Phase 4)

## Page catalogue (`index.html` → `section-shop`)
Structure : breadcrumb → titre/contexte → pills racines → panneau
sous-catégories animé → barre de filtres horizontale → toolbar
(compteur, tri, vue) → chips → grille `auto-fill minmax(230px)` → pagination.

## Catégories
Données réelles : `GET /api/categories/tree` (`CategoryService`,
hiérarchie illimitée `parent_id`). Pills = racines (+ Toutes).
Clic racine : filtre le sous-arbre + ouvre le panneau animé
(`grid-template-rows` 0fr→1fr) listant **toutes** les sous-catégories.
Re-clic : réinitialise. État actif : fond + `aria-pressed`.

## Recherche
- Autocomplete groupé (produits/catégories/marques/fournisseurs) + récentes.
- Insensible aux accents (`normStr`, NFD) côté client.
- Serveur : `ProductServlet?action=search&q=` (LIKE 6 champs, `q` borné à 100).
- Vide : état adapté ; sans résultat : empty + reset (jamais d'erreur).

## État URL (partageable)
`#/boutique?cat=&q=&brand=&sup=&stock=&min=&max=&sort=&rate=&promo=&sizes=&colors=&page=`
écrit via `history.replaceState` à chaque rendu boutique, relu au
chargement et sur `hashchange` (bouton retour navigateur).

## Filtres / tri / pagination
Client sur catalogue complet : catégorie, marque (+comptes), vendeur,
note min, promo, tailles/couleurs multi, stock, prix, recherche.
Tris : pertinence, nouveautés, ventes, notes, prix ± (cohérents).
Pagination 12/page, conserve filtres/tri. Reset global (+ URL).

## Cartes / états / a11y
`productCardHTML` unique (grille/liste) : image 4/5 + 2e au hover,
badges données-réelles, prix, vendeur, stock. Skeletons au chargement,
empty + retry, erreurs `ApiResponse`. `aria-expanded/pressed/current`,
labels, focus visible, clavier (Entrée/Escape).

## API / sécurité
Aucun contrat modifié. `q` borné serveur ; tout le reste en
`PreparedStatement` ; validation commande via `RequestValidator` (Phase 2).
