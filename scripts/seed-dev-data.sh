#!/bin/sh
# =====================================================================
#  scripts/seed-dev-data.sh — Mentora RH Backend
#  Seed de données de développement (comptes Manager, départements, employés,
#  présence historique, documents RH, demandes administratives, recrutement)
#  via l'API réelle — jamais par SQL pour les entités métier. ai-instructions.md
#  (Database rules #2) : les migrations ne portent que schéma + donnée de
#  référence ; comptes, départements, employés, demandes, offres, etc. ne sont
#  pas de la donnée de référence et doivent passer par les chemins validés de
#  l'application, comme en production.
#
#  Exception assumée : l'historique de pointages (et les anomalies qui en
#  découlent) est inséré par SQL direct dans ce script. Il n'existe aucun
#  chemin applicatif pour dater un pointage dans le passé — le scan kiosque
#  horodate toujours Instant.now() (PointageService#scanner) et la correction
#  manuelle exige un pointage déjà existant. Même principe déjà établi dans
#  AttendanceIntegrationTest#insererPointageEntreeBackdated pour les tests.
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
WEBHOOK_SECRET="${INTERNAL_WEBHOOK_SECRET:-a7f3e91c-local-dev-only-9d2b4c}"
DB_CONTAINER="rh_db"
DB_USER="rh_dev"
DB_NAME="rh_dev"

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

poster_json_avec_token() {
  # $1 = URL, $2 = corps JSON, $3 = token
  fichier="$TMPDIR_SEED/body.json"
  printf '%s' "$2" > "$fichier"
  curl -s -X POST "$1" \
    -H "Authorization: Bearer $3" -H "Content-Type: application/json; charset=utf-8" \
    --data-binary "@$fichier"
}

patch_vide() {
  # $1 = URL
  curl -s -X PATCH "$1" -H "Authorization: Bearer $TOKEN" >/dev/null
}

sql() {
  # $1 = requête SQL (une seule instruction ou bloc), exécutée dans le conteneur Postgres
  docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -q
}

sql_valeur() {
  # Comme sql() mais -t -A (tuples seuls, non alignés) : renvoie une valeur unique exploitable
  # directement en shell (ex. SELECT id FROM ... LIMIT 1), sans en-tête ni bordures.
  docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$DB_NAME" -tAq
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

mettre_a_jour_json() {
  # $1 = URL, $2 = corps JSON
  fichier="$TMPDIR_SEED/body.json"
  printf '%s' "$2" > "$fichier"
  curl -s -X PUT "$1" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json; charset=utf-8" \
    --data-binary "@$fichier"
}

extraire_id() {
  grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4
}

echo "==> Identité de l'entreprise (Configuration)..."
mettre_a_jour_json "$API/api/config/identite-entreprise" \
  '{"raisonSociale":"HB Développement","adresse":"12 Avenue Mohammed V, Tétouan 93000, Maroc",
    "telephone":"+212 5 39 71 22 33","email":"contact@hbdev.ma","ice":"001234567000089",
    "rc":"45678","ville":"Tétouan","signataireNom":"Amal Medah",
    "signataireFonction":"Responsable Ressources Humaines","signataireSexe":"FEMME"}' \
  >/dev/null

# PNG 1x1 transparent réel (pas des octets arbitraires) : FileStorageService valide le format de
# l'image avant de l'accepter, un contenu factice serait rejeté comme illisible.
LOGO_PNG="$TMPDIR_SEED/logo.png"
base64 -d > "$LOGO_PNG" <<'PNGEOF'
iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=
PNGEOF
curl -s -X POST "$API/api/config/identite-entreprise/logo" \
  -H "Authorization: Bearer $TOKEN" -F "logo=@$LOGO_PNG" >/dev/null
curl -s -X POST "$API/api/config/identite-entreprise/signature" \
  -H "Authorization: Bearer $TOKEN" -F "signature=@$LOGO_PNG" >/dev/null

# EF-EMP-18 : un Manager est aussi un employé — departementId/poste/dateEmbauche obligatoires
# désormais pour créer un compte Manager (fiche RH créée avec le compte, cf. UserService#create).
creer_manager() {
  email=$1
  nom=$2
  prenom=$3
  departement_id=$4
  poste=$5
  date_embauche=$6
  echo "==> Manager $prenom $nom ($email)..." >&2
  poster_json "$API/api/users" \
    "{\"email\":\"$email\",\"motDePasse\":\"$MANAGER_PASSWORD\",\"role\":\"manager\",\"nom\":\"$nom\",\"prenom\":\"$prenom\",\"departementId\":\"$departement_id\",\"poste\":\"$poste\",\"typeContrat\":\"CDI\",\"dateEmbauche\":\"$date_embauche\"}" \
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

# Rattache après coup le Manager au département qu'il gère — le département doit exister avant de
# pouvoir servir de fiche RH au Manager (creer_manager ci-dessus), donc l'ordre est nécessairement
# département (sans manager) -> manager (avec ce département comme fiche RH) -> rattachement.
assigner_manager_departement() {
  departement_id=$1
  nom=$2
  manager_id=$3
  echo "==> Rattachement du manager au département $nom..." >&2
  mettre_a_jour_json "$API/api/departements/$departement_id" \
    "{\"nom\":\"$nom\",\"managerId\":\"$manager_id\"}" >/dev/null
}

# $1 nom $2 prenom $3 email $4 poste $5 departementId $6 managerId $7 dateEmbauche
# $8 typeContrat $9 sexe $10 cin $11 dateFinContratPrevue(ou vide) $12 dateFinStagePrevue(ou vide)
creer_employe() {
  nom=$1; prenom=$2; email=$3; poste=$4; departement_id=$5; manager_id=$6
  date_embauche=$7; type_contrat=$8; sexe=$9; cin=${10}; fin_contrat=${11}; fin_stage=${12}
  echo "==> Employé $prenom $nom ($poste, $type_contrat)..." >&2
  body="{\"nom\":\"$nom\",\"prenom\":\"$prenom\",\"email\":\"$email\",\"poste\":\"$poste\",\"departementId\":\"$departement_id\",\"managerId\":\"$manager_id\",\"dateEmbauche\":\"$date_embauche\",\"typeContrat\":\"$type_contrat\",\"sexe\":\"$sexe\",\"cin\":\"$cin\""
  [ -n "$fin_contrat" ] && body="$body,\"dateFinContratPrevue\":\"$fin_contrat\""
  [ -n "$fin_stage" ] && body="$body,\"dateFinStagePrevue\":\"$fin_stage\""
  body="$body}"
  poster_json "$API/api/employes" "$body" | extraire_id
}

# ─────────────────────────────────────────────────────────────────────
# 1. Managers, départements, employés (volume et types variés)
# ─────────────────────────────────────────────────────────────────────

# Un seul département géré par manager (EmployeService.departementGereParManagerCourant()
# suppose exactement un département par manager). Créés sans manager dans un premier temps : un
# Manager est désormais aussi un employé (EF-EMP-18), sa fiche RH a besoin d'un departementId
# existant au moment de la création du compte.
INGENIERIE_ID=$(creer_departement "Ingénierie" "")
RH_ID=$(creer_departement "Ressources Humaines" "")
FINANCE_ID=$(creer_departement "Finance" "")

if [ -z "$INGENIERIE_ID" ] || [ -z "$RH_ID" ] || [ -z "$FINANCE_ID" ]; then
  echo "ERREUR : création d'un département a échoué, arrêt du seed (employés non créés)."
  exit 1
fi

DATE_EMBAUCHE_MANAGER=$(date -d "-800 days" +%Y-%m-%d)

KARIM_ID=$(creer_manager "karim.bennani@hbdev.ma" "Bennani" "Karim" \
  "$INGENIERIE_ID" "Manager Ingénierie" "$DATE_EMBAUCHE_MANAGER")
SARA_ID=$(creer_manager "sara.alaoui@hbdev.ma" "Alaoui" "Sara" \
  "$RH_ID" "Manager Ressources Humaines" "$DATE_EMBAUCHE_MANAGER")
YOUSSEF_ID=$(creer_manager "youssef.amrani@hbdev.ma" "Amrani" "Youssef" \
  "$FINANCE_ID" "Manager Finance" "$DATE_EMBAUCHE_MANAGER")

if [ -z "$KARIM_ID" ] || [ -z "$SARA_ID" ] || [ -z "$YOUSSEF_ID" ]; then
  echo "ERREUR : création d'un compte Manager a échoué, arrêt du seed."
  exit 1
fi

assigner_manager_departement "$INGENIERIE_ID" "Ingénierie" "$KARIM_ID"
assigner_manager_departement "$RH_ID" "Ressources Humaines" "$SARA_ID"
assigner_manager_departement "$FINANCE_ID" "Finance" "$YOUSSEF_ID"

# EF-EMP-18 : /api/users renvoie l'id du compte de connexion (utilisateurs.id) — utilisable comme
# managerId (c'est bien ce que référence employes.manager_id), mais PAS comme employeId pour une
# demande administrative sur la fiche RH du Manager lui-même, qui a son propre id (employes.id,
# différent). Recherche par e-mail (unique) sur /api/employes pour le retrouver.
obtenir_employe_id_par_email() {
  curl -s -G "$API/api/employes" \
    -H "Authorization: Bearer $TOKEN" \
    --data-urlencode "recherche=$1" --data-urlencode "size=1" \
    | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4
}

KARIM_EMPLOYE_ID=$(obtenir_employe_id_par_email "karim.bennani@hbdev.ma")

J=$(date -d "-60 days" +%Y-%m-%d)
J10=$(date -d "+10 days" +%Y-%m-%d)
J45=$(date -d "+45 days" +%Y-%m-%d)
J70=$(date -d "+70 days" +%Y-%m-%d)
NOV=$(date -d "+120 days" +%Y-%m-%d)

YASSINE_ID=$(creer_employe "Idrissi" "Yassine" "yassine.idrissi@hbdev.ma" "Développeur backend" \
  "$INGENIERIE_ID" "$KARIM_ID" "2025-03-01" "CDI" "HOMME" "AB111111" "" "")
NADIA_ID=$(creer_employe "Fassi" "Nadia" "nadia.fassi@hbdev.ma" "Développeuse frontend" \
  "$INGENIERIE_ID" "$KARIM_ID" "2025-06-15" "CDI" "FEMME" "AB111112" "" "")
OMAR_ID=$(creer_employe "Tazi" "Omar" "omar.tazi@hbdev.ma" "Chargé de recrutement" \
  "$RH_ID" "$SARA_ID" "2024-11-10" "CDI" "HOMME" "AB111113" "" "")
HIND_ID=$(creer_employe "Chraibi" "Hind" "hind.chraibi@hbdev.ma" "Gestionnaire paie" \
  "$RH_ID" "$SARA_ID" "2025-01-20" "CDI" "FEMME" "AB111114" "" "")
AMINE_ID=$(creer_employe "Berrada" "Amine" "amine.berrada@hbdev.ma" "Comptable" \
  "$FINANCE_ID" "$YOUSSEF_ID" "2024-05-05" "CDI" "HOMME" "AB111115" "" "")
SALMA_ID=$(creer_employe "Ziani" "Salma" "salma.ziani@hbdev.ma" "Contrôleuse de gestion" \
  "$FINANCE_ID" "$YOUSSEF_ID" "2026-02-01" "CDD" "FEMME" "AB111116" "$NOV" "")
KHALID_ID=$(creer_employe "Ouazzani" "Khalid" "khalid.ouazzani@hbdev.ma" "Développeur QA" \
  "$INGENIERIE_ID" "$KARIM_ID" "2023-09-01" "CDI" "HOMME" "AB111117" "" "")
MERYEM_ID=$(creer_employe "El Fassi" "Meryem" "meryem.elfassi@hbdev.ma" "Stagiaire Marketing" \
  "$RH_ID" "$SARA_ID" "$J" "STAGIAIRE" "FEMME" "" "" "$J10")
ANAS_ID=$(creer_employe "Bouzid" "Anas" "anas.bouzid@hbdev.ma" "Stagiaire Développement" \
  "$INGENIERIE_ID" "$KARIM_ID" "$(date -d "-30 days" +%Y-%m-%d)" "STAGIAIRE" "HOMME" "" "" "$J45")
GHITA_ID=$(creer_employe "Alami" "Ghita" "ghita.alami@hbdev.ma" "Stagiaire Finance" \
  "$FINANCE_ID" "$YOUSSEF_ID" "$(date -d "-20 days" +%Y-%m-%d)" "STAGIAIRE_REMUNERE" "FEMME" "" "" "$J70")

for id in "$YASSINE_ID" "$NADIA_ID" "$OMAR_ID" "$HIND_ID" "$AMINE_ID" "$SALMA_ID" "$KHALID_ID" \
  "$MERYEM_ID" "$ANAS_ID" "$GHITA_ID"; do
  if [ -z "$id" ]; then
    echo "ERREUR : création d'un employé a échoué, arrêt du seed."
    exit 1
  fi
done

# ─────────────────────────────────────────────────────────────────────
# 2. Présence : QR codes, horaire de référence, politique d'anomalies,
#    jours fériés, historique de pointages + anomalies (SQL direct)
# ─────────────────────────────────────────────────────────────────────

echo "==> Génération des QR codes badge..."
for id in "$YASSINE_ID" "$NADIA_ID" "$OMAR_ID" "$HIND_ID" "$AMINE_ID" "$SALMA_ID" "$KHALID_ID" \
  "$MERYEM_ID" "$ANAS_ID" "$GHITA_ID"; do
  curl -s -X POST "$API/api/pointages/qr-code/generer/$id" \
    -H "Authorization: Bearer $TOKEN" >/dev/null
done

echo "==> Horaire de référence..."
poster_json "$API/api/horaires-reference" \
  "{\"heureDebutMatin\":\"08:30:00\",\"heureFinMatin\":\"13:00:00\",\"heureDebutApresMidi\":\"14:00:00\",\"heureFinApresMidi\":\"17:00:00\",\"toleranceMinutes\":10,\"dateEffet\":\"$J\"}" \
  >/dev/null

echo "==> Politique d'anomalies (seuil 3 / 30 jours)..."
curl -s -X PUT "$API/api/politique-anomalies" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"seuilAnomalies":3,"periodeJours":30}' >/dev/null

echo "==> QR code de site (pointage mobile)..."
poster_json "$API/api/kiosque/sites" '{"libelle":"Siège Tétouan"}' >/dev/null

FERIE1=$(date -d "-10 days" +%Y-%m-%d)
FERIE2=$(date -d "-17 days" +%Y-%m-%d)
echo "==> Jours fériés ($FERIE1, $FERIE2)..."
poster_json "$API/api/demandes-administratives/jours-feries" \
  "{\"dateFerie\":\"$FERIE1\",\"libelle\":\"Fête du Trône (test seed)\"}" >/dev/null
poster_json "$API/api/demandes-administratives/jours-feries" \
  "{\"dateFerie\":\"$FERIE2\",\"libelle\":\"Fête du Travail (test seed)\"}" >/dev/null

echo "==> Historique de pointages (20 derniers jours ouvrés) et anomalies associées..."
sql <<SQLEOF
-- Plan déterministe par (employé, jour) : bucket 0-99 dérivé d'un hash stable, pour que
-- l'insertion des pointages et celle des anomalies restent cohérentes entre elles.
CREATE TEMP TABLE plan_jour AS
SELECT
  e.id AS employe_id,
  j.jour,
  abs((('x' || substr(md5(e.id::text || j.jour::text), 1, 8))::bit(32)::int)) % 100 AS bucket,
  abs((('x' || substr(md5(e.id::text || j.jour::text || 'r'), 1, 8))::bit(32)::int)) % 100 AS resolue_bucket
FROM employes e
JOIN qr_codes qr ON qr.employe_id = e.id AND qr.actif = true
CROSS JOIN (
  SELECT gs::date AS jour
  FROM generate_series(current_date - interval '20 days', current_date - interval '1 day', interval '1 day') gs
  WHERE extract(isodow FROM gs) < 6
    AND gs::date NOT IN ('$FERIE1', '$FERIE2')
) j
WHERE e.email IN (
  'yassine.idrissi@hbdev.ma','nadia.fassi@hbdev.ma','omar.tazi@hbdev.ma','hind.chraibi@hbdev.ma',
  'amine.berrada@hbdev.ma','salma.ziani@hbdev.ma','khalid.ouazzani@hbdev.ma',
  'meryem.elfassi@hbdev.ma','anas.bouzid@hbdev.ma','ghita.alami@hbdev.ma'
);

-- bucket 0-4   (5%)  : absence totale (aucun pointage)
-- bucket 5-14  (10%) : retard à l'arrivée (entrée 08:55-09:20)
-- bucket 15-24 (10%) : départ anticipé (sortie 16:00-16:25)
-- bucket 25-34 (10%) : absence de check-out (entrée seule)
-- bucket 35-99 (65%) : présence normale

INSERT INTO pointages (employe_id, qr_code_id, type_scan, horodatage, horaire_reference_id)
SELECT
  p.employe_id,
  qr.id,
  'entree'::type_scan_pointage,
  (p.jour + CASE
     WHEN p.bucket BETWEEN 5 AND 14 THEN time '08:55' + (random() * interval '25 minutes')
     ELSE time '08:25' + (random() * interval '10 minutes')
   END)::timestamptz,
  h.id
FROM plan_jour p
JOIN qr_codes qr ON qr.employe_id = p.employe_id AND qr.actif = true
CROSS JOIN (SELECT id FROM horaires_reference ORDER BY date_effet DESC LIMIT 1) h
WHERE p.bucket >= 5;

INSERT INTO pointages (employe_id, qr_code_id, type_scan, horodatage, horaire_reference_id)
SELECT
  p.employe_id,
  qr.id,
  'sortie'::type_scan_pointage,
  (p.jour + CASE
     WHEN p.bucket BETWEEN 15 AND 24 THEN time '16:00' + (random() * interval '25 minutes')
     ELSE time '17:00' + (random() * interval '15 minutes')
   END)::timestamptz,
  h.id
FROM plan_jour p
JOIN qr_codes qr ON qr.employe_id = p.employe_id AND qr.actif = true
CROSS JOIN (SELECT id FROM horaires_reference ORDER BY date_effet DESC LIMIT 1) h
WHERE p.bucket >= 5 AND p.bucket NOT BETWEEN 25 AND 34;

-- Anomalies correspondantes (retard / départ anticipé / absence de check-out)
INSERT INTO anomalies_pointage (employe_id, date_pointage, type_anomalie, pointage_entree_id, pointage_sortie_id, resolue)
SELECT p.employe_id, p.jour, 'retard'::type_anomalie_pointage,
  (SELECT id FROM pointages WHERE employe_id = p.employe_id AND type_scan = 'entree'
     AND horodatage::date = p.jour LIMIT 1),
  NULL, p.resolue_bucket < 30
FROM plan_jour p WHERE p.bucket BETWEEN 5 AND 14;

INSERT INTO anomalies_pointage (employe_id, date_pointage, type_anomalie, pointage_entree_id, pointage_sortie_id, resolue)
SELECT p.employe_id, p.jour, 'depart_anticipe'::type_anomalie_pointage,
  NULL,
  (SELECT id FROM pointages WHERE employe_id = p.employe_id AND type_scan = 'sortie'
     AND horodatage::date = p.jour LIMIT 1),
  p.resolue_bucket < 30
FROM plan_jour p WHERE p.bucket BETWEEN 15 AND 24;

INSERT INTO anomalies_pointage (employe_id, date_pointage, type_anomalie, pointage_entree_id, pointage_sortie_id, resolue)
SELECT p.employe_id, p.jour, 'absence_checkout'::type_anomalie_pointage,
  (SELECT id FROM pointages WHERE employe_id = p.employe_id AND type_scan = 'entree'
     AND horodatage::date = p.jour LIMIT 1),
  NULL, p.resolue_bucket < 30
FROM plan_jour p WHERE p.bucket BETWEEN 25 AND 34;
SQLEOF

echo "==> Désactivation de Khalid Ouazzani (démission, pour certificat de travail)..."
DATE_DEPART=$(date -d "-3 days" +%Y-%m-%d)
poster_json "$API/api/employes/$KHALID_ID/desactiver" \
  "{\"motif\":\"demission\",\"dateDepart\":\"$DATE_DEPART\"}" >/dev/null

# ─────────────────────────────────────────────────────────────────────
# 2bis. Conformité RH Maroc (CNSS/AMO/CIMR/RIB/période d'essai, salaire)
# ─────────────────────────────────────────────────────────────────────

echo "==> Conformité RH Maroc (CNSS/AMO/CIMR/RIB, période d'essai)..."
# PUT /api/employes/{id} remplace la fiche entière (EmployeModificationRequete) : on renvoie les
# champs déjà connus de la création plus les nouveaux champs de conformité.
PERIODE_ESSAI_YASSINE=$(date -d "2025-03-01 +90 days" +%Y-%m-%d)
mettre_a_jour_json "$API/api/employes/$YASSINE_ID" \
  "{\"nom\":\"Idrissi\",\"prenom\":\"Yassine\",\"email\":\"yassine.idrissi@hbdev.ma\",\"poste\":\"Développeur backend\",\"dateEmbauche\":\"2025-03-01\",\"typeContrat\":\"CDI\",\"sexe\":\"HOMME\",\"cin\":\"AB111111\",\"numeroCnss\":\"7712345\",\"numeroAmo\":\"AM99887766\",\"numeroCimr\":\"CIMR445566\",\"rib\":\"230 780 0123456789012345 67\",\"periodeEssaiFinLe\":\"$PERIODE_ESSAI_YASSINE\"}" \
  >/dev/null

PERIODE_ESSAI_HIND=$(date -d "2025-01-20 +90 days" +%Y-%m-%d)
mettre_a_jour_json "$API/api/employes/$HIND_ID" \
  "{\"nom\":\"Chraibi\",\"prenom\":\"Hind\",\"email\":\"hind.chraibi@hbdev.ma\",\"poste\":\"Gestionnaire paie\",\"dateEmbauche\":\"2025-01-20\",\"typeContrat\":\"CDI\",\"sexe\":\"FEMME\",\"cin\":\"AB111114\",\"numeroCnss\":\"7723456\",\"numeroAmo\":\"AM99887755\",\"numeroCimr\":\"CIMR445577\",\"rib\":\"230 780 0198765432109876 54\",\"periodeEssaiFinLe\":\"$PERIODE_ESSAI_HIND\"}" \
  >/dev/null

echo "==> Salaire brut mensuel (pour l'attestation de salaire)..."
mettre_a_jour_json "$API/api/employes/$YASSINE_ID/salaire" '{"salaireBrutMensuel":14500.00}' >/dev/null
mettre_a_jour_json "$API/api/employes/$HIND_ID/salaire" '{"salaireBrutMensuel":11000.00}' >/dev/null

# ─────────────────────────────────────────────────────────────────────
# 3. Documents RH
# ─────────────────────────────────────────────────────────────────────

echo "==> Documents RH (certificats, document libre)..."
poster_json "$API/api/documents/employes/$MERYEM_ID/certificat-stage" \
  "{\"sujetStage\":\"Élaboration d'une campagne de marque employeur\"}" >/dev/null
poster_json "$API/api/documents/employes/$ANAS_ID/certificat-stage" \
  "{\"sujetStage\":\"Développement d'un module de reporting présence\"}" >/dev/null
curl -s -X POST "$API/api/documents/employes/$YASSINE_ID/attestation-travail" \
  -H "Authorization: Bearer $TOKEN" >/dev/null
curl -s -X POST "$API/api/documents/employes/$HIND_ID/attestation-travail" \
  -H "Authorization: Bearer $TOKEN" >/dev/null
curl -s -X POST "$API/api/documents/employes/$YASSINE_ID/attestation-salaire" \
  -H "Authorization: Bearer $TOKEN" >/dev/null
curl -s -X POST "$API/api/documents/employes/$KHALID_ID/certificat-travail" \
  -H "Authorization: Bearer $TOKEN" >/dev/null

DOC_LIBRE="$TMPDIR_SEED/attestation-bancaire.txt"
printf 'Document RH de test (seed) — attestation bancaire.\n' > "$DOC_LIBRE"
curl -s -X POST "$API/api/documents/employes/$NADIA_ID/document-libre" \
  -H "Authorization: Bearer $TOKEN" -F "file=@$DOC_LIBRE" >/dev/null

# ─────────────────────────────────────────────────────────────────────
# 4. Demandes administratives
# ─────────────────────────────────────────────────────────────────────

echo "==> Demandes administratives (congés, statuts variés)..."

creer_demande_conge() {
  # $1 employeId $2 dateDebut $3 dateFin $4 granularite $5 motif
  poster_json "$API/api/demandes-administratives" \
    "{\"employeId\":\"$1\",\"typeDemande\":\"conge\",\"granularite\":\"$4\",\"dateDebut\":\"$2\",\"dateFin\":\"$3\",\"motif\":\"$5\"}" \
    | extraire_id
}

D1=$(creer_demande_conge "$NADIA_ID" "$(date -d "+15 days" +%Y-%m-%d)" "$(date -d "+19 days" +%Y-%m-%d)" \
  "journee" "Congés annuels")
D2=$(creer_demande_conge "$OMAR_ID" "$(date -d "+5 days" +%Y-%m-%d)" "$(date -d "+5 days" +%Y-%m-%d)" \
  "demi_matin" "Rendez-vous médical")
D3=$(creer_demande_conge "$AMINE_ID" "$(date -d "+8 days" +%Y-%m-%d)" "$(date -d "+10 days" +%Y-%m-%d)" \
  "journee" "Événement familial")
D4=$(creer_demande_conge "$HIND_ID" "$(date -d "-25 days" +%Y-%m-%d)" "$(date -d "-22 days" +%Y-%m-%d)" \
  "journee" "Congés annuels")
D5=$(creer_demande_conge "$SALMA_ID" "$(date -d "+3 days" +%Y-%m-%d)" "$(date -d "+3 days" +%Y-%m-%d)" \
  "demi_apres_midi" "Formalité administrative")

[ -n "$D4" ] && patch_vide "$API/api/demandes-administratives/$D4/approuver"
[ -n "$D5" ] && patch_vide "$API/api/demandes-administratives/$D5/rejeter"
# D1, D2, D3 restent "en_attente" pour la démo de la file d'approbation.

echo "==> Congés légaux spéciaux (mariage, naissance, décès, maladie avec justificatif)..."

creer_demande_speciale() {
  # $1 employeId $2 typeDemande $3 dateDebut $4 dateFin $5 motif $6 fichierDocumentLibreId(ou vide)
  fichier_json=""
  [ -n "$6" ] && fichier_json=",\"fichierDocumentLibreId\":\"$6\""
  poster_json "$API/api/demandes-administratives" \
    "{\"employeId\":\"$1\",\"typeDemande\":\"$2\",\"dateDebut\":\"$3\",\"dateFin\":\"$4\",\"motif\":\"$5\"$fichier_json}" \
    | extraire_id
}

D6=$(creer_demande_speciale "$OMAR_ID" "conge_mariage" \
  "$(date -d "+30 days" +%Y-%m-%d)" "$(date -d "+33 days" +%Y-%m-%d)" "Mariage")
D7=$(creer_demande_speciale "$AMINE_ID" "conge_naissance" \
  "$(date -d "+2 days" +%Y-%m-%d)" "$(date -d "+4 days" +%Y-%m-%d)" "Naissance de mon enfant")
D8=$(creer_demande_speciale "$SALMA_ID" "conge_deces" \
  "$(date -d "-2 days" +%Y-%m-%d)" "$(date -d "-1 days" +%Y-%m-%d)" "Décès d'un proche")

# La maladie exige un justificatif déjà téléversé (fichierDocumentLibreId) avant la création —
# LocalDiskFileStorage n'accepte que pdf/doc/docx/jpeg/png (NFR-SEC-07), un .txt serait rejeté.
# PDF minimal mais valide (une page vide), pas un contenu arbitraire renommé en .pdf.
JUSTIFICATIF="$TMPDIR_SEED/arret-travail.pdf"
printf '%%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\nxref\n0 4\n0000000000 65535 f \ntrailer<</Size 4/Root 1 0 R>>\nstartxref\n0\n%%%%EOF' > "$JUSTIFICATIF"
# La réponse est { "data": "<uuid>" } (UUID brut, pas un objet {"id":...}) — extraire_id ne
# correspond pas à ce format, on extrait donc directement le champ "data".
FICHIER_JUSTIFICATIF_ID=$(curl -s -X POST "$API/api/demandes-administratives/justificatif" \
  -H "Authorization: Bearer $TOKEN" -F "fichier=@$JUSTIFICATIF" \
  | grep -o '"data":"[^"]*"' | cut -d'"' -f4)
if [ -n "$FICHIER_JUSTIFICATIF_ID" ]; then
  D9=$(creer_demande_speciale "$NADIA_ID" "conge_maladie" \
    "$(date -d "-1 days" +%Y-%m-%d)" "$(date -d "+1 days" +%Y-%m-%d)" \
    "Grippe, arrêt médical" "$FICHIER_JUSTIFICATIF_ID")
  [ -n "$D9" ] && patch_vide "$API/api/demandes-administratives/$D9/approuver"
fi

[ -n "$D6" ] && patch_vide "$API/api/demandes-administratives/$D6/approuver"
# D7, D8 restent "en_attente" pour varier les statuts affichés.

echo "==> Bon de sortie, demande « Autre », demande de document, congé d'un Manager..."

D10=$(poster_json "$API/api/demandes-administratives" \
  "{\"employeId\":\"$AMINE_ID\",\"typeDemande\":\"bon_sortie\",\"dateDebut\":\"$(date -d '+1 days' +%Y-%m-%d)\",\"heureDepart\":\"14:00:00\",\"heureRetourPrevue\":\"16:30:00\",\"motif\":\"Rendez-vous à la banque\"}" \
  | extraire_id)

D11=$(poster_json "$API/api/demandes-administratives" \
  "{\"employeId\":\"$GHITA_ID\",\"typeDemande\":\"autre\",\"motif\":\"Demande de badge d'accès parking\"}" \
  | extraire_id)

# EF-ADM-15 : laissée "en_attente" à dessein — c'est le scénario de démo pour le bouton
# "Envoyer un document" (DemandesPage#EnvoyerDocumentModal), qui remplace Approuver pour ce type.
D12=$(poster_json "$API/api/demandes-administratives" \
  "{\"employeId\":\"$NADIA_ID\",\"typeDemande\":\"demande_document\",\"motif\":\"Attestation de travail pour un dossier bancaire\"}" \
  | extraire_id)

# EF-EMP-18 : un Manager est aussi un employé — démontre qu'il peut lui-même poser un congé comme
# n'importe quel employé, sur sa propre fiche RH créée avec son compte.
# Fenêtre de 5 jours (pas 2) : garantit au moins un jour ouvré quel que soit le jour d'exécution
# du seed — un congé de 2 jours calendaires tombant intégralement sur un week-end donnerait une
# durée ouvrée nulle et ferait échouer la création ("La durée du congé doit être positive").
D13=$(poster_json "$API/api/demandes-administratives" \
  "{\"employeId\":\"$KARIM_EMPLOYE_ID\",\"typeDemande\":\"conge\",\"granularite\":\"journee\",\"dateDebut\":\"$(date -d '+12 days' +%Y-%m-%d)\",\"dateFin\":\"$(date -d '+16 days' +%Y-%m-%d)\",\"motif\":\"Congés annuels\"}" \
  | extraire_id)

[ -n "$D10" ] && patch_vide "$API/api/demandes-administratives/$D10/approuver"
[ -n "$D13" ] && patch_vide "$API/api/demandes-administratives/$D13/approuver"
# D11, D12 restent "en_attente".

# ─────────────────────────────────────────────────────────────────────
# 5. Recrutement
# ─────────────────────────────────────────────────────────────────────

echo "==> Recrutement (offres, candidatures, pipeline)..."

creer_offre() {
  # $1 intitule $2 departementId $3 motsCles(JSON array) $4 categorie(ou vide)
  categorie_json=""
  [ -n "$4" ] && categorie_json=",\"categorie\":\"$4\""
  poster_json "$API/api/offres" \
    "{\"intitule\":\"$1\",\"description\":\"Poste à pourvoir — description de test (seed).\",\"departementId\":\"$2\",\"motsClesRequis\":$3$categorie_json}" \
    | extraire_id
}

OFFRE_DEV_ID=$(creer_offre "Développeur Full-Stack" "$INGENIERIE_ID" '["java","react","typescript"]' "Ingénieurs IA")
OFFRE_RH_ID=$(creer_offre "Assistant RH" "$RH_ID" '["administratif","paie","recrutement"]' "")
OFFRE_FIN_ID=$(creer_offre "Analyste Financier" "$FINANCE_ID" '["finance","excel","comptabilite"]' "")
OFFRE_STAGE_ID=$(creer_offre "Stagiaire Développement Web" "$INGENIERIE_ID" '["html","css","javascript"]' "Stagiaires")

ingerer_candidature() {
  # $1 email $2 nom $3 sujet $4 corps
  curl -s -X POST "$API/api/recruitment/ingest" \
    -H "X-Internal-Webhook-Secret: $WEBHOOK_SECRET" \
    -F "emailExpediteur=$1" -F "nomExpediteur=$2" -F "sujet=$3" -F "corps=$4" \
    | extraire_id
}

C1=$(ingerer_candidature "sofia.mansouri@example.com" "Sofia Mansouri" "Candidature Développeur Full-Stack" \
  "Bonjour, je postule pour le poste de développeur full-stack. Cordialement, Sofia.")
C2=$(ingerer_candidature "reda.benjelloun@example.com" "Reda Benjelloun" "Candidature poste développeur" \
  "Bonjour, merci de trouver ma candidature ci-jointe. Reda.")
C3=$(ingerer_candidature "imane.saadi@example.com" "Imane Saadi" "Candidature Assistant RH" \
  "Bonjour, je suis intéressée par le poste d'assistant RH. Imane.")
C4=$(ingerer_candidature "youssef.tahiri@example.com" "Youssef Tahiri" "Candidature Analyste Financier" \
  "Bonjour, veuillez trouver ma candidature pour le poste d'analyste financier. Youssef.")
C5=$(ingerer_candidature "lina.chakib@example.com" "Lina Chakib" "Candidature développeur" \
  "Bonjour, candidature spontanée pour un poste de développeur. Lina.")
C6=$(ingerer_candidature "karim.jabri@example.com" "Karim Jabri" "Candidature Assistant RH" \
  "Bonjour, je souhaite postuler au poste d'assistant RH. Karim.")

# Fait avancer certaines candidatures dans le pipeline (recu -> preselectionne -> entretien / rejete).
[ -n "$C1" ] && poster_json "$API/api/candidatures/$C1/statut" '{"statut":"preselectionne"}' >/dev/null
[ -n "$C1" ] && poster_json "$API/api/candidatures/$C1/statut" \
  "{\"statut\":\"entretien\",\"managerId\":\"$KARIM_ID\",\"dateEntretien\":\"$(date -d '+4 days' +%Y-%m-%d)T10:00:00Z\"}" >/dev/null

[ -n "$C2" ] && poster_json "$API/api/candidatures/$C2/statut" '{"statut":"preselectionne"}' >/dev/null

[ -n "$C3" ] && poster_json "$API/api/candidatures/$C3/statut" '{"statut":"preselectionne"}' >/dev/null
[ -n "$C3" ] && poster_json "$API/api/candidatures/$C3/statut" \
  "{\"statut\":\"entretien\",\"managerId\":\"$SARA_ID\",\"dateEntretien\":\"$(date -d '+6 days' +%Y-%m-%d)T14:30:00Z\"}" >/dev/null

[ -n "$C5" ] && poster_json "$API/api/candidatures/$C5/statut" \
  '{"statut":"rejete","corpsMessage":"Profil ne correspondant pas au poste actuellement ouvert."}' >/dev/null

# C4, C6 restent "recu" — file d'attente de tri initial.

# ─────────────────────────────────────────────────────────────────────
# 6. Blocage des congés (EF-ADM-12, Admin uniquement)
# ─────────────────────────────────────────────────────────────────────

echo "==> Période de blocage des congés..."
# Fenêtre volontairement loin (+200/+210 jours) : ne doit chevaucher aucune des demandes de congé
# déjà créées ci-dessus (toutes entre +2 et +33 jours), sinon leur création aurait échoué.
poster_json "$API/api/demandes-administratives/periodes-blocage-conges" \
  "{\"dateDebut\":\"$(date -d '+200 days' +%Y-%m-%d)\",\"dateFin\":\"$(date -d '+210 days' +%Y-%m-%d)\",\"libelle\":\"Période de forte activité — congés suspendus\"}" \
  >/dev/null

# ─────────────────────────────────────────────────────────────────────
# 7. Délégation temporaire d'approbation (EF-AUTH-11/12, Admin uniquement)
# ─────────────────────────────────────────────────────────────────────

echo "==> Délégation active (Admin -> Sara Alaoui)..."
poster_json "$API/api/delegations" \
  "{\"delegueId\":\"$SARA_ID\",\"dateDebut\":\"$(date -d '-1 days' +%Y-%m-%d)\",\"dateFin\":\"$(date -d '+7 days' +%Y-%m-%d)\"}" \
  >/dev/null

# ─────────────────────────────────────────────────────────────────────
# 8. Variété de statuts d'activation kiosque (pointage mobile)
# ─────────────────────────────────────────────────────────────────────

# Chaque employé reçoit déjà un code personnel "en_attente" à sa création (cf. §1) — sans ce qui
# suit, KiosqueActivationsPanel n'afficherait jamais que ce seul statut en démo. "Active" n'a pas
# de chemin applicatif simulable simplement (il faudrait saisir le vrai code reçu par e-mail sur
# /pointage-mobile) : laissé de côté plutôt que de fragiliser le seed avec une dépendance à
# l'API Mailpit. "Révoquée" (chemin applicatif réel) et "Expirée" (aucun chemin applicatif — même
# exception assumée que le backdating des pointages, cf. en-tête de ce fichier) sont couvertes.
echo "==> Variété de statuts d'activation kiosque (Révoquée, Expirée)..."

ACTIVATION_ID_GHITA=$(printf '%s' \
  "SELECT id FROM kiosque_activations WHERE employe_id = '$GHITA_ID' LIMIT 1;" | sql_valeur)
if [ -n "$ACTIVATION_ID_GHITA" ]; then
  curl -s -X POST "$API/api/kiosque/activations/$ACTIVATION_ID_GHITA/revoquer" \
    -H "Authorization: Bearer $TOKEN" >/dev/null
fi

sql <<SQLEOF
UPDATE kiosque_activations SET emis_le = now() - interval '30 hours'
WHERE employe_id = '$MERYEM_ID';
SQLEOF

echo "==> Seed terminé."
echo "    Admin   : $ADMIN_EMAIL / $ADMIN_PASSWORD"
echo "    Manager : karim.bennani@hbdev.ma / $MANAGER_PASSWORD (Ingénierie)"
echo "    Manager : sara.alaoui@hbdev.ma / $MANAGER_PASSWORD (Ressources Humaines)"
echo "    Manager : youssef.amrani@hbdev.ma / $MANAGER_PASSWORD (Finance)"
echo "    10 employés (dont 3 stagiaires, 1 CDD, 1 désactivé) + 3 Managers ayant chacun leur"
echo "    propre fiche RH (EF-EMP-18), 20 jours de pointages, anomalies, 2 jours fériés,"
echo "    13 demandes administratives (congé, bon de sortie, autre, demande de document,"
echo "    congés mariage/naissance/décès/maladie, dont un congé posé par un Manager sur sa"
echo "    propre fiche), 4 offres (1 catégorisée « Stagiaires »), 6 candidatures, conformité"
echo "    RH Maroc et salaire renseignés sur 2 fiches, attestation de salaire générée, QR de"
echo "    site pour le pointage mobile. Un code de pointage mobile personnel a aussi été"
echo "    envoyé par e-mail (Mailpit) à chacun des 10 employés à leur création."
echo "    Demande de document « en_attente » sur Nadia Fassi : à traiter via le bouton"
echo "    « Envoyer un document » (Demandes / approbation) pour tester ce flux."
echo "    Période de blocage des congés (+200/+210j), délégation active Admin -> Sara Alaoui"
echo "    (Ressources Humaines, aujourd'hui -> +7j), activations kiosque variées (Ghita Alami"
echo "    révoquée, Meryem El Fassi expirée) pour démontrer les filtres de chaque écran."
