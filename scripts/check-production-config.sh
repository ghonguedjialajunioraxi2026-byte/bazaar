#!/usr/bin/env bash
# MBOA STYLE — Vérification de la configuration de production (Phase 21).
# N'affiche JAMAIS les valeurs des secrets (présence uniquement).
# Retourne 0 si tout est configuré, 1 sinon.
set -u

fail=0
check_set() {
  local name="$1" required="${2:-yes}"
  if [ -z "${!name:-}" ]; then
    if [ "$required" = "yes" ]; then echo "MANQUANT: $name"; fail=1;
    else echo "OPTIONNEL absent: $name"; fi
  else
    echo "OK: $name (défini, valeur masquée)"
  fi
}

echo "== obligatoires =="
check_set DB_URL
check_set DB_USER
check_set DB_PASSWORD

echo "== application =="
check_set APP_ENV no
check_set APP_BASE_URL no
check_set MBOA_UPLOADS_DIR no
check_set GOOGLE_CLIENT_ID no

echo "== pawapay (sandbox d'abord, valeurs masquées) =="
check_set PAWAPAY_API_TOKEN no
check_set PAWAPAY_API_URL no
check_set PAWAPAY_ENVIRONMENT no
case "${PAWAPAY_ENVIRONMENT:-sandbox}" in
  production)
    echo "AVERTISSEMENT: PAWAPAY_ENVIRONMENT=production (argent réel — valider le sandbox d'abord)."
    ;;
esac

echo "== garde-fous =="
if [ "${DB_URL:-}" = "*localhost*" ]; then :; fi
case "${DB_URL:-}" in
  *localhost*|*127.0.0.1*)
    echo "AVERTISSEMENT: DB_URL pointe vers localhost (OK en dev/test, à changer en production)."
    ;;
esac
case "${APP_BASE_URL:-}" in
  http://*)
    echo "AVERTISSEMENT: APP_BASE_URL en HTTP (HTTPS requis en production)."
    ;;
esac
if [ -n "${DB_PASSWORD:-}" ] && [ "${#DB_PASSWORD}" -lt 12 ]; then
  echo "AVERTISSEMENT: DB_PASSWORD court (< 12 caractères)."
fi

if [ "$fail" -ne 0 ]; then echo "CONFIG INCOMPLÈTE" >&2; exit 1; fi
echo "CONFIG OK"
