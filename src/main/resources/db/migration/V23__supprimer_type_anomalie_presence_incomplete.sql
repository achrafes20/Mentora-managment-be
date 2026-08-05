-- EF-ATT-04 : retrait de 'presence_incomplete' du type type_anomalie_pointage. Ce type d'anomalie
-- était censé représenter une absence totale (ni entrée ni sortie), mais le moteur de détection
-- nocturne (AnomalieService#analyserJourPourEmploye) ne traite que les employés ayant déjà eu au
-- moins un pointage ce jour-là (findEmployeIdsAvecPointageEntre) — le cas "ni entrée ni sortie"
-- n'est donc jamais atteignable avec le code actuel. Seul le script de seed le générait
-- artificiellement en SQL brut, en dehors du moteur applicatif.
--
-- Postgres n'a pas d'ALTER TYPE ... DROP VALUE : on recrée le type sans cette valeur, on convertit
-- la colonne, on supprime l'ancien type. Les éventuelles lignes déjà en 'presence_incomplete' sont
-- supprimées avant la conversion (donnée dérivée du moteur d'anomalies, jamais de la donnée de
-- référence — cf. ai-instructions.md).
DELETE FROM anomalies_pointage WHERE type_anomalie = 'presence_incomplete';

CREATE TYPE type_anomalie_pointage_new AS ENUM ('retard', 'depart_anticipe', 'absence_checkout');

ALTER TABLE anomalies_pointage
    ALTER COLUMN type_anomalie TYPE type_anomalie_pointage_new
    USING type_anomalie::text::type_anomalie_pointage_new;

DROP TYPE type_anomalie_pointage;

ALTER TYPE type_anomalie_pointage_new RENAME TO type_anomalie_pointage;
