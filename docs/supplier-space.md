# MBOA STYLE — Espace fournisseur (Phase 9)

## Authentification / rôles
Email + mot de passe (hash, min 6) ou Google OAuth ; sessions 12h
(`supplier_sessions`, révocables). Aucun rôle supplémentaire : l'ID
vient **toujours** du token, jamais du frontend (§39).

## Isolation (testée A→B : 404 partout)
Produits (CRUD, stock, statut), commandes (lecture/statut), conversations,
offres, statistiques : chaque accès est re-vérifié en DAO/service par
`supplier_id` de session. Profils : champs autorisés uniquement
(nom, contacts, ville, adresse, description, logo) + validation
(`RequestValidator`) ; rôle/commission/solde intouchables.

## Produits / stock / variantes
CRUD + images + statut (PENDING→ validation admin ; REJECTED modifié →
PENDING). Suppression **refusée** si commandes liées (historique
préservé, pas d'archive nécessaire). Stock global (pas de stock/variante,
documenté Phase 5). Création : nom/prix/stock/catégorie validés serveur.

## Commandes / statuts / messagerie / négociation
Commandes du vendeur uniquement, machine Phase 7 enforced.
Messagerie Phase 8 réutilisée + vue **Négociations** (offres PENDING
toutes conversations, badge, accepter/refuser/contre-proposer).

## Boutique publique
`#/boutique/{id}` : logo/nom/ville/description, nb produits, note
moyenne calculée, grille produits, états vides/404. Données réelles
(`action=find` + catalogue local).

## Statistiques / uploads / notifications
Dashboard existant vérifié vs SQL (produits/commandes exacts) ; CA =
montant brut commandes (documenté, pas "bénéfice"). Uploads : extensions,
10 Mo, dossier persistant, anti path-traversal. Notifs existantes.

## Sécurité
Double-clic : `saveProduct` désactivé + spinner (`finally`) ; 401/403
JSON propres ; XSS échappée ; aucune donnée sensible loggée.
Limites : pas de portefeuille/retraits/commissions (Phase 10+).
