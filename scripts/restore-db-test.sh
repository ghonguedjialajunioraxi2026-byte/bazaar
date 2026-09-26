#!/usr/bin/env bash
# MBOA STYLE — Test de restauration dans une base ISOLÉE (Phase 21).
# Usage : ./scripts/restore-db-test.sh <fichier.sql.gz> <base_isolee>
# REFUSE de restaurer dans mboa_style (production) sans confirmation explicite.
set -euo pipefail

FILE="${1:?Usage: restore-db-test.sh <fichier.sql.gz> <base_isolee>}"
ISOLATED="${2:?Usage: restore-db-test.sh <fichier.sql.gz> <base_isolee>}"

if [ "$ISOLATED" = "mboa_style" ] && [ "${CONFIRM_PROD_RESTORE:-}" != "YES-I-AM-SURE" ]; then
  echo "REFUSÉ: restauration vers '$ISOLATED' sans CONFIRM_PROD_RESTORE=YES-I-AM-SURE" >&2
  exit 1
fi

export MYSQL_PWD="${DB_PASSWORD:-}"
mysql -h localhost -P 3306 -u "${DB_USER:-root}" -e "CREATE DATABASE IF NOT EXISTS \`$ISOLATED\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
gzip -dc "$FILE" | mysql -h localhost -P 3306 -u "${DB_USER:-root}" "$ISOLATED"
unset MYSQL_PWD

echo "== vérification =="
export MYSQL_PWD="${DB_PASSWORD:-}"
mysql -h localhost -P 3306 -u "${DB_USER:-root}" -e "USE \`$ISOLATED\`; SHOW TABLES;" "$ISOLATED"
mysql -h localhost -P 3306 -u "${DB_USER:-root}" -N -e \
  "SELECT CONCAT('products=', (SELECT COUNT(*) FROM \`$ISOLATED\`.products), ' orders=', (SELECT COUNT(*) FROM \`$ISOLATED\`.orders));"
unset MYSQL_PWD
echo "RESTAURATION TEST OK dans $ISOLATED"
