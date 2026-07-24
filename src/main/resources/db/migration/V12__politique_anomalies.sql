-- =====================================================================
--  V12__politique_anomalies.sql
--  Migration Flyway — EF-ATT-11 : seuil d'anomalies de pointage non résolues (sur une période
--  donnée) au-delà duquel une alerte est notifiée au Manager du département de l'employé.
--  Table à une seule ligne logique (même principe que identite_entreprise, T4.B2).
--  Valeurs par défaut : 3 anomalies sur une fenêtre glissante de 30 jours.
-- =====================================================================

CREATE TABLE politique_anomalies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seuil_anomalies     INT NOT NULL CHECK (seuil_anomalies > 0),
    periode_jours       INT NOT NULL CHECK (periode_jours > 0),
    modifie_par         UUID REFERENCES utilisateurs(id),
    modifie_le          TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO politique_anomalies (seuil_anomalies, periode_jours) VALUES (3, 30);
