# MBOA STYLE — Configuration & développement

## Variables d'environnement
| Variable | Défaut | Usage |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/mboa_style?...Africa/Douala` | JDBC |
| `DB_USER` / `DB_PASSWORD` | `root` / mot de passe local | JDBC |
| `GOOGLE_CLIENT_ID` | ID MBOA STYLE en dur | OAuth fournisseur |
| `MBOA_UPLOADS_DIR` | `~/mboa-style-uploads` | fichiers persistants |
| `APP_ENV` | non défini (= dev) | `development`/`test`/`production` (documentaire) |
| `APP_BASE_URL` | non défini | URL publique (ex. `https://mboastyle.cm`, Phase 22) |
| `CORS_ALLOWED_ORIGINS` | non défini (= same-origin strict) | origines externes éventuelles |
| `PAYMENT_PROVIDER` | `MANUAL` | COD + Mobile Money manuel (aucun PSP) |
| `LOG_LEVEL` | `INFO` | niveau de logs |

Modèle complet : `.env.example` (ne jamais committer `.env`). Contrôle :
`scripts/check-production-config.sh`. Détail production :
`docs/production-readiness.md`, `docs/database-production.md`.

## Environnements
- **Dev** : Java 17, Maven 3, Tomcat 10.1 (`/tmp/opencode/apache-tomcat-10.1.59`, port 8082), MySQL 8 local.
- **Test** : JUnit 5 (`mvn test`) ; staging = base `mboa_verify` jetable.
- **Prod** : non déployé (phases 21–23) ; prérequis : secrets via env, `MBOA_UPLOADS_DIR` persistant, backup `mysqldump` + binlogs.

## Commandes
```bash
mvn clean package          # build WAR -> target/mboa-style.war
mvn test                   # tests unitaires
cp target/mboa-style.war $CATALINA_HOME/webapps/ROOT.war
mysql -u root -p mboa_style < database.sql   # base vide uniquement
```

## Conventions
Java : `PascalCase` classes, `camelCase` méthodes, `UPPER_SNAKE` constantes.
SQL : snake_case, FK nommées (recommandé `fk_*` à terme).
JS : `camelCase`, fonctions globales par page (dette : split futur).
Réponses API : évolutions additives uniquement.
