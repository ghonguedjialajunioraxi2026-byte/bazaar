# MBOA STYLE — Base de données (`mboa_style`, MySQL 8, utf8mb4)

Schéma de référence : `database.sql` (19 tables, 15 FK, 0 orphelin vérifié).
Migrations idempotentes : `database/migrate_*.sql`. Seed : `database/seed_categories.sql`.

## Domaines
- **Catalogue** : `categories` (hiérarchie `parent_id → categories.id`, `ON DELETE SET NULL`), `products` (`category_id`, `supplier_id`, `status` ENUM PENDING/APPROVED/REJECTED/ACTIVE/INACTIVE), `product_images` (CASCADE), `promotions` (CASCADE), `reviews` (CASCADE).
- **Commerce** : `orders` (statuts EN_ATTENTE…ANNULEE), `order_items` (CASCADE commande), `payments` (CASCADE commande), `delivery_zones`, `stock_alerts` (CASCADE produit).
- **Acteurs** : `admins`, `admin_sessions` (CASCADE), `suppliers` (soldes, commission, `google_id` unique), `supplier_sessions` (CASCADE), `supplier_payments`.
- **Relation** : `conversations` (CASCADE fournisseur), `messages` (CASCADE conversation), `notifications` (rôles ADMIN/SUPER_ADMIN/GESTIONNAIRE/CLIENT/SUPPLIER + priorité).
- **Système** : `activity_logs` (admin nullable, `ON DELETE SET NULL`).

## Règles
- Ne jamais refondre sans justification ; toute évolution = migration idempotente versionnée.
- `database.sql` ne contient **aucun `USE`** : s'importe avec `mysql … <db> < database.sql` (garde-fou post-incident).
- Données de test : ne jamais importer de dump de prod ailleurs ; backups via `mysqldump` + binary logs activés.
