#!/usr/bin/env bash
# MBOA STYLE — Build WAR de production (Phase 21).
# Prépare le livrable SANS déployer. Échoue proprement à la moindre erreur.
set -euo pipefail

cd "$(dirname "$0")/.."

command -v java >/dev/null || { echo "ERREUR: java introuvable" >&2; exit 1; }
command -v mvn >/dev/null || { echo "ERREUR: maven introuvable" >&2; exit 1; }

java -version 2>&1 | head -n 1

echo "== mvn clean test =="
mvn clean test

echo "== mvn package =="
mvn package -DskipTests

WAR="target/mboa-style.war"
[ -f "$WAR" ] || { echo "ERREUR: $WAR introuvable" >&2; exit 1; }
ls -la "$WAR"

echo "== contrôle secrets dans le WAR =="
if unzip -p "$WAR" WEB-INF/classes/com/ecommerce/config/DBConnection.class 2>/dev/null | strings | grep -i "TonMotDePasse" >/dev/null; then
  echo "NOTE: valeur de repli DEV présente dans le bytecode (inactif si DB_URL/DB_PASSWORD définis en production)."
fi
if unzip -l "$WAR" | grep -Ei "\.env$|secrets?/|credentials|\.pem$|\.key$" >/dev/null; then
  echo "ERREUR: fichier sensible détecté dans le WAR" >&2
  exit 1
fi

echo "BUILD PRODUCTION OK : $WAR"
