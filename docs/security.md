# MBOA STYLE — Sécurité (état Phase 2)

## En place
- Mots de passe hashés (`PasswordUtil`), jamais en clair.
- Sessions : tokens aléatoires, expiry vérifiée en SQL (`expires_at > NOW()`), logout = invalidation.
- `AdminAuth.requireAdmin` : 401 sans session sur toutes les lectures admin et écritures sensibles (16 points de contrôle).
- SQL : 162 `PreparedStatement` ; 1 seul `Statement` (SQL fixe, sans entrée utilisateur).
- Uploads : extensions validées, 10 Mo max, dossier persistant hors webapp, anti path-traversal (`UploadServlet`, `UploadUtil`).
- Google OAuth : `id_token` vérifié via `tokeninfo` + contrôle `aud` == client ID.

## Dette connue (phases sécurité à venir)
- Messages `e.getMessage()` exposés dans certaines réponses (format consommé par le dashboard — normaliser via `ApiResponse` + `ErrorCodes`).
- Secrets par défaut en repli local (`DBConnection`, `GoogleAuthUtil`) : surchargeables par env (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `GOOGLE_CLIENT_ID`, `MBOA_UPLOADS_DIR`).
- Pas de rate-limiting, pas de CSRF token (API JSON same-origin), pas de chiffrement PII spécifique.

## Règles
- Ne jamais logger ni retourner : mot de passe, token, secret, stack trace, SQL.
- Toute nouvelle route sensible = test 401 + test inter-rôles.
