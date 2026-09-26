# MBOA STYLE — Déploiement (Phase 22)

## 1. Architecture production

```
Internet → HTTPS (443) → Nginx → http://127.0.0.1:8080 → Tomcat 10.1 → MBOA STYLE (ROOT.war) → MySQL 8
```

Répertoires (convention ; adapter à l'hôte réel) :

```
/opt/mboa-style/        (/tmp/mboa-style dans la répétition locale Phase 22)
├── app/tomcat/         (CATALINA_BASE dédié, apps par défaut supprimées)
├── app/releases/mboa-style-<version>.war
├── app/current.war -> releases/... (lien vers la version servie)
├── uploads/            (MBOA_UPLOADS_DIR, sauvegardé)
├── backups/            (mysqldump gzip, hors application)
├── logs/
└── config/prod.env     (chmod 600, jamais en Git)
```

## 2. Prérequis

Java 17 (`java -version`, `JAVA_HOME`), Tomcat 10.1 (Jakarta EE 10),
MySQL 8 (`utf8mb4`), Nginx (reverse proxy + TLS), `mysqldump`, `openssl`.
Mémoire : `-Xms256m -Xmx1024m` minimum (adapter, `bin/setenv.sh`).

## 3. Variables d'environnement

Voir `.env.example`. Obligatoires : `DB_URL`, `DB_USER`, `DB_PASSWORD`.
Contrôle : `scripts/check-production-config.sh` (valeurs masquées).
Ne jamais committer `.env` (`.gitignore`).

## 4. Installation Java / Tomcat

Java 17, ne pas supprimer les autres JDK. Tomcat dédié par application :
`server.xml` — shutdown `8006`/localhost, HTTP `8080` + `URIEncoding="UTF-8"`,
`redirectPort="443"`, AJP désactivé, apps `docs/examples/host-manager/manager`
supprimées, une seule copie de MBOA STYLE (`ROOT.war`).

## 5. MySQL

Base `utf8mb4_unicode_ci`, `serverTimezone=Africa/Douala` côté JDBC.
Ordre d'installation : `database.sql` puis 12 migrations (ordre :
`docs/database-production.md` §1 — **snapshot avant offers**).
Compte applicatif least-privilege :

```sql
CREATE USER 'mboa_app'@'app-host' IDENTIFIED BY '<fort>';
GRANT SELECT, INSERT, UPDATE, DELETE ON mboa_style.* TO 'mboa_app'@'app-host';
```

MySQL ne doit pas être exposé à Internet (`bind-address 127.0.0.1` ou
firewall ; constat local Phase 22 : `0.0.0.0:3306` → à restreindre en vrai prod).

## 6. Reverse proxy / HTTPS / domaine / firewall

Modèle : `deploy/nginx-mboastyle.conf` (Nginx non installé en local Phase 22).
TLS via certificat du domaine réel (ex. certbot + renouvellement auto),
redirection 80→443, `X-Forwarded-Proto/For`, `client_max_body_size 32m`,
cache `/assets|/uploads` 7 j, `no-store` sur `/api/`.
Ports publics : 80/443 uniquement (+ SSH restreint). `mboastyle.cm` :
placeholder à confirmer avant DNS.

## 7. Google OAuth / paiements / CORS / headers

- OAuth : client prod dédié (origines + redirect HTTPS), `GOOGLE_CLIENT_ID`
  via env ; faux token → 401 (testé). État Phase 22 : **NON ACTIVÉ** (pas de
  credential prod).
- Paiements : `PAYMENT_PROVIDER=MANUAL` (COD + Mobile Money vérifié).
  **STATUS = NOT YET ACTIVATED** pour tout PSP externe (aucune clé LIVE).
- CORS : aucune origine émise (same-origin). Headers applicatifs : nosniff,
  SAMEORIGIN, referrer, permissions (HSTS derrière HTTPS uniquement).

## 8. Uploads

`MBOA_UPLOADS_DIR` persistant, permissions lecture/écriture Tomcat,
sauvegardé avec la DB. Sécurité : UUID, magic bytes, SVG refusé, 10 Mo
(testé Phases 20/22).

## 9. Déploiement WAR

1. `scripts/build-production.sh` (tests + package + contrôle secrets).
2. Copier vers `releases/mboa-style-<version>.war`, garder la version
   précédente pour rollback.
3. Backup DB (`scripts/backup-db.sh`) AVANT toute migration.
4. `Tomcat STOP → ROOT.war remplacé → START → logs → /api/health`.

## 10. Redémarrage / logs / health

Logs : `tomcat/logs/catalina.out` (+ accès), `activity_logs` applicatifs.
Aucune stack sensible côté client (pages 404/500 + JSON générique).
Health : `GET /api/health` → `{"status":"UP"}` / `503 DOWN` (DB incluse).

## 11. Backup / rollback

Rollback WAR : repointer `current.war` vers la release précédente +
restart + health. Rollback DB : uniquement si nécessaire (migrations
potentiellement irréversibles) via `scripts/restore-db-test.sh` avec
`CONFIRM_PROD_RESTORE=YES-I-AM-SURE` + backup préalable.

## 12. Dépannage

Voir `docs/production-runbook.md`. Incident Phase 22 : arrêt ordonné
inattendu de Tomcat (23:29, cause non établie) → restart + recommandation
`systemd Restart=always` en vrai prod.

## 13. Version déployée (répétition locale Phase 22)

- Version : 1.0.1 (1.0.0 initiale + fix avis 404 BUG-P23-001, voir
  `docs/production-test-report.md`) — WAR : `releases/mboa-style-1.0.1.war`
  (5,0 Mo), rollback : `releases/mboa-style-1.0.0.war` conservé
- Date : 2026-09-27 — serveur : local isolé `/tmp/mboa-style` (pas de serveur public)
- DB : `mboa_style_prod` (fraîche : `database.sql` + 12 migrations, backup pré-déploiement conservé)
- Tomcat 10.1.59 (port 8080, JVM 256m/1024m) — Java 17.0.19
- Domaine/HTTPS/OAuth/paiements LIVE : NON CONFIGURÉS (conditions Phase 23)
