# MBOA STYLE — API (`/api/*`, JSON UTF-8)

Légende auth : `PUB` public · `ADM` token admin · `SUP` token fournisseur.

| Méthode | URL | Auth | Rôle |
|---|---|---|---|
| POST | `/api/admin/login` | PUB | login admin → `{token,username,role}` |
| GET | `/api/admin/dashboard|stats|logs` | ADM | stats, logs |
| POST | `/api/admin/logout`, `/api/admin/product/validate` | ADM | logout, APPROVED/REJECTED |
| GET | `/api/products[?action=popular|new|promo|topRated|recommended|search|byCategory|filter]` | PUB | catalogue |
| POST/PUT/DELETE | `/api/products` | ADM | CRUD admin |
| GET | `/api/categories`, `/tree`, `/roots`, `/{id}[/children|breadcrumb]` | PUB | catégories |
| POST/PUT/PATCH/DELETE | `/api/categories…` | ADM | CRUD + statut |
| GET/POST | `/api/orders` | PUB | liste / création commande |
| PUT/DELETE | `/api/orders` | ADM | statut / suppression |
| GET/POST | `/api/reviews` | PUB | lecture / dépôt avis |
| PUT/DELETE | `/api/reviews` | ADM | modération |
| GET | `/api/suppliers` | PUB | vitrines |
| POST/PUT/DELETE | `/api/suppliers` | ADM | CRUD |
| POST | `/api/supplier/login|register|google` | PUB | auth fournisseur |
| GET | `/api/supplier/google-client-id` | PUB | client ID OAuth |
| * | `/api/supplier/me|dashboard|products|orders|…` | SUP | espace fournisseur |
| GET | `/api/delivery`, `/api/promotions`, `/api/product-images`, `/api/payments` | PUB lecture | référentiels |
| Écritures | mêmes endpoints | ADM | CRUD |
| GET/POST/PUT/DELETE | `/api/notifications` | ADM | centre admin |
| GET | `/uploads/*` | PUB | fichiers persistants |
| GET | `/sitemap.xml` | PUB | SEO |

## Conventions
- Succès : données brutes **ou** `{success:true,…}` (coexistent, voir `ApiResponse` pour la cible).
- Erreurs : `{"error"|"message":…}` + HTTP adapté ; nouveaux codes `ErrorCodes` (`AUTH_REQUIRED`, `VALIDATION_ERROR`, `RESOURCE_NOT_FOUND`, `CONFLICT`, `INTERNAL_ERROR`…).
- Règle : ne jamais casser un contrat — toute évolution est **additive** (champs ajoutés, jamais renommés/supprimés sans adapter le frontend).
