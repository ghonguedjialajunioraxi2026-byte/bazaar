# MBOA STYLE — Architecture

```
CLIENT (HTML/JS + Tailwind CDN)
  │  HTTP / JSON
  ▼
SERVLETS  (16, `controller/`) — HTTP, auth requête, validation simple, JSON
  │  appelle
  ▼
SERVICES  (6, `service/`) — règles métier, orchestration DAO, transactions
  │  utilise
  ▼
DAO       (17, `dao/`) — SQL PreparedStatement, mapping ResultSet ↔ Model
  │  JDBC
  ▼
MYSQL 8 (`mboa_style`, 19 tables, 15 FK)
```

## Couches et responsabilités

| Couche | Contenu | Doit faire | Ne doit pas faire |
|---|---|---|---|
| Controller | 16 servlets `/api/*` | params, auth (`AdminAuth`/tokens), validation d'entrée, codes HTTP, JSON | SQL, logique métier, transactions |
| Service | Admin/Category/Order/Product/Supplier/SupplierAuth | règles métier, multi-DAO, statuts, notifications | SQL direct, HTTP |
| DAO | 17 classes | CRUD, mapping, `try-with-resources`, commit/rollback | règles métier |
| Model | 14 POJO | données | logique |
| Util | Json/Password/Slug/GoogleAuth/Upload/AdminAuth/ApiResponse | transverse | état global |

## Authentification
- Admin : `admin_sessions.token` (8h), header `Authorization: Bearer`.
- Fournisseur : `supplier_sessions.token` (12h) + Google OAuth (`google_id`).
- Invité : lecture publique + création commande/avis.

## Décisions assumées
- Pas de framework (Jakarta pur), pas de DTO systématique (modèles exposés, à affiner par endpoint), pas de pool externe (connexions courtes `try-with-resources`).
