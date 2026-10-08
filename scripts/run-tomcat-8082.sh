#!/usr/bin/env bash
# MBOA STYLE — Redémarrage Tomcat BAZAAR (port 8082) avec variables .env.
# Principe : .env -> environnement du conteneur -> System.getenv("PAWAPAY_API_TOKEN").
# SÉCURITÉ : ce script n'affiche JAMAIS les valeurs (présence uniquement),
# ne contient AUCUN secret en clair, ne modifie pas le code Deposit.
set -euo pipefail
cd "$(dirname "$0")/.."

CONTAINER="mboa-style-8082"
WAR="$PWD/target/mboa-style.war"
ENV_FILE="$PWD/.env"

[ -f "$WAR" ] || { echo "ERREUR: $WAR introuvable (lancer 'mvn package' d'abord)" >&2; exit 1; }
[ -f "$ENV_FILE" ] || { echo "ERREUR: $ENV_FILE introuvable" >&2; exit 1; }

# 1. Charger .env en mémoire (sans afficher les valeurs).
set -a
# shellcheck disable=SC1091
source "$ENV_FILE"
set +a

# 2. Vérification masquée : présence uniquement, jamais les valeurs.
mask_check() {
  local name="$1"
  local val="${!name:-}"
  if [ -z "$val" ]; then echo "$name = MISSING";
  elif [[ "$val" == *"TON_TOKEN_ICI"* ]]; then echo "$name = PLACEHOLDER (remplacer par le vrai token Sandbox dans .env)";
  else echo "$name = PRESENT"; fi
}
echo "== .env =="
mask_check PAWAPAY_API_TOKEN
mask_check PAWAPAY_API_URL
mask_check PAWAPAY_DEPOSIT_URL
mask_check PAWAPAY_ENVIRONMENT

# 3. Reprendre les identifiants DB du conteneur existant (sans les afficher,
#    sans les écrire dans ce fichier) pour ne pas casser la config qui marche.
prev_env() { docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$CONTAINER" 2>/dev/null | sed -n "s/^$1=//p" | tail -n 1; }
DB_URL="${DB_URL:-$(prev_env DB_URL)}"
DB_USER="${DB_USER:-$(prev_env DB_USER)}"
DB_PASSWORD="${DB_PASSWORD:-$(prev_env DB_PASSWORD)}"
DB_URL="${DB_URL:-jdbc:mysql://172.17.0.1:3306/mboa_style_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Douala}"
DB_USER="${DB_USER:-root}"
if [ -z "$DB_PASSWORD" ]; then echo "ERREUR: DB_PASSWORD introuvable (ni env ni conteneur $CONTAINER)" >&2; exit 1; fi

# 4. Recréer le conteneur avec le même mapping (8082->8080, WAR en ROOT.war)
#    + les variables PawaPay issues du .env.
#    Uploads persistants : les logos/produits survivent aux relances
#    (sans volume, /root/mboa-style-uploads meurt avec le conteneur).
UPLOADS_DIR="$PWD/uploads-data"
mkdir -p "$UPLOADS_DIR"
docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$CONTAINER" \
  -p 8082:8080 \
  -v "$WAR:/usr/local/tomcat/webapps/ROOT.war:ro" \
  -v "$UPLOADS_DIR:/root/mboa-style-uploads" \
  -e DB_URL="$DB_URL" \
  -e DB_USER="$DB_USER" \
  -e DB_PASSWORD="$DB_PASSWORD" \
  -e PAWAPAY_API_TOKEN="${PAWAPAY_API_TOKEN:-}" \
  -e PAWAPAY_API_URL="${PAWAPAY_API_URL:-https://api.sandbox.pawapay.io}" \
  -e PAWAPAY_DEPOSIT_URL="${PAWAPAY_DEPOSIT_URL:-https://api.sandbox.pawapay.io/v2/deposits}" \
  -e PAWAPAY_ENVIRONMENT="${PAWAPAY_ENVIRONMENT:-sandbox}" \
  tomcat:10.1-jdk17 >/dev/null
echo "Conteneur $CONTAINER relancé."

# 5. Attendre le démarrage puis contrôle masqué.
for i in $(seq 1 24); do
  if curl -sf -o /dev/null http://localhost:8082/ 2>/dev/null; then break; fi
  sleep 5
done
echo "== config-status (sans secret) =="
curl -s http://localhost:8082/api/payments/pawapay/config-status; echo
