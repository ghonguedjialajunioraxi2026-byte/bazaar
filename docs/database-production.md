# MBOA STYLE — Base de données : installation, migrations, production

## 1. Installation neuve (ordre strict)

```bash
mysql -u root -p -e "CREATE DATABASE mboa_style CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p mboa_style < database.sql
```

Puis appliquer les migrations **dans cet ordre** (toutes idempotentes :
ré-exécutables sans erreur) :

| # | Fichier | Contenu |
|---|---|---|
| 1 | `database/migrate_products_status.sql` | colonne `products.status` |
| 2 | `database/migrate_categories_v2.sql` | hiérarchie catégories v2 |
| 3 | `database/seed_categories.sql` | catégories initiales |
| 4 | `database/migrate_delivery.sql` | zones + expéditions |
| 5 | `database/migrate_monetization.sql` | ledger + règles |
| 6 | `database/migrate_notifications_v2.sql` | notifications v2 |
| 7 | `database/migrate_notifications_v3.sql` | notifications v3 |
| 8 | `database/migrate_order_snapshot.sql` | snapshot lignes commande (**avant** offers : `migrate_offers` fait `ADD COLUMN … AFTER product_name`) |
| 9 | `database/migrate_offers.sql` | négociation (dépend de `order_items.product_name`) |
| 10 | `database/migrate_order_number_unique.sql` | unicité numéro commande |
| 11 | `database/migrate_payments.sql` | paiements |
| 12 | `database/migrate_perf_indexes.sql` | index performance |

```bash
for f in migrate_products_status migrate_categories_v2 seed_categories \
         migrate_delivery migrate_monetization migrate_notifications_v2 \
         migrate_notifications_v3 migrate_order_snapshot migrate_offers \
         migrate_order_number_unique migrate_payments migrate_perf_indexes; do
  mysql -u root -p mboa_style < "database/$f.sql"
done
```

Version DB résultante : schéma `database.sql` (2026-09-24) + 12 migrations.
Aucune table `schema_version` historique : la version se déduit des colonnes
présentes (ex. `products.status`, `payments.provider`). Ne pas réordonner.

## 2. Compte applicatif production (least privilege)

Ne JAMAIS utiliser `root` en production. Créer un utilisateur dédié :

```sql
CREATE USER 'mboa_app'@'app-host' IDENTIFIED BY '<mot-de-passe-fort-unique>';
GRANT SELECT, INSERT, UPDATE, DELETE ON mboa_style.* TO 'mboa_app'@'app-host';
FLUSH PRIVILEGES;
```

Justification : l'application n'exécute que du DML via JDBC (`SELECT/INSERT/
UPDATE/DELETE`). Pas de `CREATE/DROP/ALTER/GRANT` au runtime (les migrations
sont appliquées manuellement par l'exploitant avec un compte admin).
Vérification : `SHOW GRANTS FOR 'mboa_app'@'app-host';`

Configurer ensuite : `DB_URL`, `DB_USER=mboa_app`, `DB_PASSWORD` (variables
d'environnement du service Tomcat, jamais en Git).

## 3. Encodage / timezone

- Base : `utf8mb4 / utf8mb4_unicode_ci` (emoji, français accentué).
- Connexion JDBC : `serverTimezone=Africa/Douala` (voir `DBConnection`).
- Timezone serveur MySQL : de préférence `Africa/Douala` ou UTC avec
  conversion applicative ; l'essentiel est la cohérence commandes/paiements/
  livraisons/logs (horodatage `TIMESTAMP`/`DATETIME` existants conservés,
  aucune réécriture d'historique).

## 4. Sauvegarde

Procédure officielle : `scripts/backup-db.sh` (mysqldump
`--single-transaction --routines --triggers --events`, gzip, nommage
`mboa_style_YYYYMMDD_HHMMSS.sql.gz`).

- Fréquence recommandée : quotidienne (automatiser via cron Phase 22) +
  avant chaque déploiement/migration.
- Conservation : 30 jours glissants minimum, 1 copie hors serveur.
- Restauration testée le 2026-09-27 : backup `mboa_style_test` → restauration
  `mboa_restore_check` → 25 tables, 47 produits, 80 commandes, relations OK.
- Restauration : `scripts/restore-db-test.sh` (refuse la base de production
  sans `CONFIRM_PROD_RESTORE=YES-I-AM-SURE`).
- Ne jamais restaurer un backup de test sur la production.

## 5. Données initiales (seed)

- `seed_categories.sql` : catégories de la boutique (rejouable, production OK).
- Premier administrateur : **aucun compte par défaut en base**. Procédure
  (Phase 22, sur serveur) : hacher un mot de passe fort via `PasswordUtil`
  (PBKDF2), `INSERT INTO admins (username, password_hash, role)`, connexion,
  vérification du rôle, suppression de tout compte de démonstration.
- Interdit en production : `admin/admin`, comptes `demo/test`, fausses
  commandes, faux chiffres.
