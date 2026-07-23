#!/bin/sh
# =====================================================================
#  scripts/seed-dev-data.sh — Mentora RH Backend
#  Seed de données de développement (comptes Manager + départements +
#  employés) via l'API réelle — jamais par SQL. ai-instructions.md (Database
#  rules #2) :
#  les migrations ne portent que schéma + donnée de référence (horaire
#  par défaut, jours fériés, paramètres système, seed Admin) ; comptes et
#  départements ne sont pas de la donnée de référence et doivent passer
#  par les chemins validés de l'application, comme en production.
#
#  Prérequis : stack démarrée (`make up-d` ou `make reset-db`) sur une
#  base fraîche — pensé pour être rejoué après chaque volume neuf, pas
#  pour être idempotent sur une base déjà peuplée (emails/noms uniques).
# =====================================================================

set -e

API="${VITE_API_BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="admin@hbdev.ma"
ADMIN_PASSWORD="admin123"
MANAGER_PASSWORD="Manager@2026"

TMPDIR_SEED=$(mktemp -d)
trap 'rm -rf "$TMPDIR_SEED"' EXIT

# Les accents (é, è...) passés directement en argument -d à curl finissent parfois mal encodés
# selon le shell/terminal hôte (repro Windows Git Bash) ; écrire le JSON dans un fichier puis
# --data-binary @fichier lit les octets bruts et contourne ce souci, quel que soit l'hôte.
poster_json() {
  # $1 = URL, $2 = corps JSON
  fichier="$TMPDIR_SEED/body.json"
  printf '%s' "$2" > "$fichier"
  curl -s -X POST "$1" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json; charset=utf-8" \
    --data-binary "@$fichier"
}

echo "==> Attente du backend ($API)..."
i=0
while [ "$i" -lt 30 ]; do
  if curl -sf "$API/actuator/health" >/dev/null 2>&1; then
    break
  fi
  i=$((i + 1))
  sleep 2
done
if ! curl -sf "$API/actuator/health" >/dev/null 2>&1; then
  echo "ERREUR : backend indisponible sur $API après 60s, seed annulé."
  exit 1
fi

echo "==> Connexion Admin ($ADMIN_EMAIL)..."
TOKEN=$(curl -s -X POST "$API/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"motDePasse\":\"$ADMIN_PASSWORD\"}" \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "ERREUR : échec de connexion Admin, seed annulé (compte seed absent ? migrations à jour ?)."
  exit 1
fi

extraire_id() {
  grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4
}

creer_manager() {
  email=$1
  nom=$2
  prenom=$3
  echo "==> Manager $prenom $nom ($email)..." >&2
  poster_json "$API/api/users" \
    "{\"email\":\"$email\",\"motDePasse\":\"$MANAGER_PASSWORD\",\"role\":\"manager\",\"nom\":\"$nom\",\"prenom\":\"$prenom\"}" \
    | extraire_id
}

creer_departement() {
  nom=$1
  manager_id=$2
  echo "==> Département $nom..." >&2
  if [ -n "$manager_id" ]; then
    body="{\"nom\":\"$nom\",\"managerId\":\"$manager_id\"}"
  else
    body="{\"nom\":\"$nom\"}"
  fi
  poster_json "$API/api/departements" "$body" | extraire_id
}

creer_employe() {
  nom=$1
  prenom=$2
  poste=$3
  departement_id=$4
  manager_id=$5
  date_embauche=$6
  echo "==> Employé $prenom $nom ($poste)..." >&2
  body="{\"nom\":\"$nom\",\"prenom\":\"$prenom\",\"poste\":\"$poste\",\"departementId\":\"$departement_id\",\"managerId\":\"$manager_id\",\"dateEmbauche\":\"$date_embauche\",\"typeContrat\":\"CDI\"}"
  poster_json "$API/api/employes" "$body" >&2
  echo >&2
}

KARIM_ID=$(creer_manager "karim.bennani@hbdev.ma" "Bennani" "Karim")
SARA_ID=$(creer_manager "sara.alaoui@hbdev.ma" "Alaoui" "Sara")

if [ -z "$KARIM_ID" ] || [ -z "$SARA_ID" ]; then
  echo "ERREUR : création d'un compte Manager a échoué, arrêt du seed."
  exit 1
fi

# Un seul département géré par manager (EmployeService.departementGereParManagerCourant()
# suppose exactement un département par manager — ne jamais réassigner un second manager
# déjà affecté ailleurs, sous peine de NonUniqueResultException còté backend).
INGENIERIE_ID=$(creer_departement "Ingénierie" "$KARIM_ID")
RH_ID=$(creer_departement "Ressources Humaines" "$SARA_ID")
creer_departement "Finance" "" >/dev/null

if [ -z "$INGENIERIE_ID" ] || [ -z "$RH_ID" ]; then
  echo "ERREUR : création d'un département a échoué, arrêt du seed (employés non créés)."
  exit 1
fi

creer_employe "Idrissi" "Yassine" "Développeur backend" "$INGENIERIE_ID" "$KARIM_ID" "2025-03-01"
creer_employe "Fassi" "Nadia" "Développeuse frontend" "$INGENIERIE_ID" "$KARIM_ID" "2025-06-15"
creer_employe "Tazi" "Omar" "Chargé de recrutement" "$RH_ID" "$SARA_ID" "2024-11-10"
creer_employe "Chraibi" "Hind" "Gestionnaire paie" "$RH_ID" "$SARA_ID" "2025-01-20"

echo "==> Seed terminé."
echo "    Admin   : $ADMIN_EMAIL / $ADMIN_PASSWORD"
echo "    Manager : karim.bennani@hbdev.ma / $MANAGER_PASSWORD (Ingénierie, 2 employés)"
echo "    Manager : sara.alaoui@hbdev.ma / $MANAGER_PASSWORD (Ressources Humaines, 2 employés)"
