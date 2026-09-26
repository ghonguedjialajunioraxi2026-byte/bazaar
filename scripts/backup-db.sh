#!/usr/bin/env bash
# MBOA STYLE — Sauvegarde MySQL (Phase 21).
# Usage : DB_URL=... DB_USER=... DB_PASSWORD=... ./scripts/backup-db.sh [nom_base] [dossier]
# Ne supprime rien. Nomme : mboa_style_YYYYMMDD_HHMMSS.sql.gz
set -euo pipefail

DB_NAME="${1:-mboa_style}"
OUT_DIR="${2:-./backups}"
STAMP="$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUT_DIR"
OUT="$OUT_DIR/${DB_NAME}_${STAMP}.sql.gz"

# Hôte/port extraits de DB_URL si possible, sinon localhost:3306.
HOST="localhost"; PORT="3306"
if [ -n "${DB_URL:-}" ]; then
  HOSTPORT="$(echo "$DB_URL" | sed -n 's#.*://\([^/]*\)/.*#\1#p')"
  HOST="$(echo "$HOSTPORT" | cut -d: -f1)"
  PORT="$(echo "$HOSTPORT" | cut -s -d: -f2)"
  [ -z "$PORT" ] && PORT="3306"
fi

export MYSQL_PWD="${DB_PASSWORD:-}"
mysqldump -h "$HOST" -P "$PORT" -u "${DB_USER:-root}" \
  --single-transaction --routines --triggers --events \
  "$DB_NAME" | gzip > "$OUT"
unset MYSQL_PWD

ls -la "$OUT"
echo "BACKUP OK : $OUT"
echo "Vérification :"
gzip -dc "$OUT" | head -n 5
