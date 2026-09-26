# MBOA STYLE — Production Readiness (Phase 21)

STATUT : **READY WITH CONDITIONS** (voir §8). Aucun déploiement effectué
(Phase 22), aucune donnée réelle touchée.

## 1. ENVIRONNEMENT

| Composant | Exigé | Vérifié Phase 21 |
|---|---|---|
| Java | 17 | 17.0.19 ✅ (`mvn clean test` + WAR) |
| Maven | 3.6+ | 3.6.3 ✅ |
| Tomcat | 10.1+ (Jakarta EE 10) | 10.1.59 local ✅ |
| MySQL | 8 | 8.0.46, `utf8mb4` ✅ |
| Navigateurs | Chrome/Firefox/Safari récents | Chrome headless (Phase 19/20) ✅ |

- [x] Java
- [x] Maven
- [x] Tomcat
- [x] MySQL

## 2. CONFIGURATION

Séparation `development / test / production` par variables d'environnement
(convention existante conservée) : `DB_URL`, `DB_USER`, `DB_PASSWORD`,
`MBOA_UPLOADS_DIR`, `GOOGLE_CLIENT_ID`, (+ `APP_ENV`, `APP_BASE_URL`,
`CORS_ALLOWED_ORIGINS`, `LOG_LEVEL`, `PAYMENT_PROVIDER` documentées).

- [x] variables d'environnement (`.env.example`, jamais de vraies valeurs en Git)
- [x] secrets (aucun secret réel en code ; repli DEV averti au démarrage)
- [x] URLs (frontend relatif + `apiBase()` contextuel, SEO `baseUrl(req)` dynamique)
- [x] CORS (aucune origine émise ; `CORS_ALLOWED_ORIGINS` réservée si besoin futur)
- [x] HTTPS (checklist §7, certificats à fournir Phase 22)

Vérification automatique : `scripts/check-production-config.sh` (ne révèle
jamais les valeurs).

## 3. BASE DE DONNÉES

- [x] schéma (`database.sql` + 12 migrations idempotentes ordonnées, voir `docs/database-production.md`)
- [x] migrations (ordre + idempotence vérifiés)
- [x] backup (`scripts/backup-db.sh`, testé 2026-09-27 : 44 Ko gzip)
- [x] restauration (testée : `mboa_restore_check`, 25 tables, 47 produits, 80 commandes, relations OK)

Compte prod : `mboa_app` least-privilege (`SELECT/INSERT/UPDATE/DELETE`),
jamais `root` (procédure SQL documentée).

## 4. APPLICATION

- [x] WAR (`target/mboa-style.war` 5,2 Mo, Java 17, Jakarta EE 10, sans secret)
- [x] pages d'erreur (404 + 500 brandées, `noindex`, sans stack trace ; API en JSON générique)
- [x] health check (`GET /api/health` → `{"status":"UP"}` + contrôle DB, `503` sinon, `no-store`, sans secret)
- [x] uploads (UUID, magic bytes, SVG refusé, 10 Mo, `MBOA_UPLOADS_DIR` ; `./backups` et uploads ignorés par Git)
- [x] logs (aucun mot de passe/token/secret ; avertissement `[SECURITE]` sans valeur)

## 5. AUTHENTIFICATION

- [x] sessions (opaques, Bearer header, `?token=` refusé, logout invalide, 401)
- [x] Google OAuth (vérification serveur `aud`, table checklist : Client ID/secret prod, origines, redirect URI HTTPS, console Google — **à fournir Phase 22**)
- [x] cookies (aucun cookie d'auth ; si ajout futur : `Secure/HttpOnly/SameSite`)

## 6. PAIEMENTS

`PAYMENT_PROVIDER=MANUAL` : COD + Mobile Money manuel vérifié, montants
serveur, idempotence. **STATUS externe = NOT CONFIGURED** (aucun PSP, aucune
clé LIVE en Git). Webhooks : aucun en local ; contrat `verify/fail` admin
testé. Clés production : sans objet (pas de PSP), à redéfinir si PSP ajouté.

- [x] configuration (manuelle, documentée)
- [x] webhooks (aucun ; procédure à créer si PSP)
- [x] idempotence (testée Phase 20)
- [x] clés production à fournir (sans objet / N/A)

## 7. INFRASTRUCTURE

```
Internet → HTTPS → Reverse Proxy (Nginx, à configurer Phase 22) → Tomcat → MBOA STYLE → MySQL
```

- [ ] domaine (placeholder `https://mboastyle.cm` déjà utilisé en canonical/robots/JSON-LD — **à confirmer**, sinon remplacer)
- [ ] DNS (Phase 22)
- [ ] reverse proxy (exemple à adapter Phase 22 : `proxy_pass http://127.0.0.1:8080/`, `X-Forwarded-Proto/For`, cache `/assets/*` + `/uploads/*`, `no-store` sur `/api/*` privés)
- [ ] HTTPS (certificat à fournir ; redirection 80→443, `Secure`, HSTS derrière HTTPS uniquement)

## 8. OBSERVABILITÉ

- [x] logs (stratégie : `catalina.out` + accès Tomcat ; catégories applicatives
  via `activity_logs` ; sans données sensibles)
- [x] monitoring (base : `/api/health` UP/DOWN + code HTTP ; alertes minimales
  documentées : 5xx, échecs auth, échecs paiement, disque, `activity_logs`)
- [x] espace disque (seuils : alerte 80 %, critique 90 % ; postes : MySQL,
  uploads, logs, WAR, backups)

## 9. ROLLBACK

`Backup (scripts/backup-db.sh) → déploiement → smoke (`/api/health` + accueil
+ commande test) → si problème → redeploy WAR précédent + restauration backup
→ vérification`. WAR versionné : `pom.xml 1.0.0` + tag Git par release (Phase 22).

- [x] backup
- [x] version précédente (WAR + tag)
- [x] procédure (ci-dessus)

## 10. VALIDATION

- [x] build (`mvn clean test` 62/62, `mvn package` OK, WAR inspecté)
- [x] tests (Phase 19 : 80/80 ; Phase 20 : 111/111)
- [x] smoke checklist (accueil, catalogue, recherche, sitemap/robots, produit, health — smoke complet Phase 19/20 sur build équivalent)

## 11. CE QUI RESTE À FOURNIR MANUELLEMENT (Phase 22)

1. Serveur + DNS + certificat HTTPS pour le domaine confirmé.
2. `DB_URL/DB_USER(mboa_app)/DB_PASSWORD` forts + compte least-privilege créé.
3. Google OAuth prod (client dédié, origines/redirect HTTPS) si connexion Google requise au lancement.
4. Reverse proxy Nginx (config + cache statique + `no-store` API).
5. Cron backup quotidien + copie hors serveur, rétention 30 j.
6. Création du premier admin (procédure `docs/database-production.md` §5).
7. `MBOA_UPLOADS_DIR` persistant + sauvegardé.
8. Tag Git de release `v1.0.0`.

## 12. RISQUES RÉSIDUELS

Voir `docs/security-hardening-tests.md` §20 + : repli `DB_PASSWORD` dev dans
l'historique Git (forcer env en prod), apps Tomcat par défaut à retirer en
prod, pas de CSP (décision documentée), OAuth réel non rejoué, charge massive
non testée, e-mails/SMS/push : **NOT CONFIGURED** (notifications in-app
uniquement, aucun faux système créé).

## 13. COMMANDES IMPORTANTES

```bash
./scripts/build-production.sh        # WAR + contrôles (tests inclus)
./scripts/check-production-config.sh # présence des variables (valeurs masquées)
./scripts/backup-db.sh [base] [dir]  # mysqldump + gzip
./scripts/restore-db-test.sh <gz> <base_isolee>  # restauration isolée (garde prod)
curl -s https://<domaine>/api/health # {"status":"UP"}
```

## 14. FICHIERS CRÉÉS/MODIFIÉS (Phase 21)

Créés : `HealthServlet.java`, `500.html`, `.env.example`, `scripts/*.sh` (×4),
`docs/database-production.md`, `docs/production-readiness.md` (ce fichier).
Modifiés : `web.xml` (page 500), `.gitignore` (secrets/uploads/logs/backups).
