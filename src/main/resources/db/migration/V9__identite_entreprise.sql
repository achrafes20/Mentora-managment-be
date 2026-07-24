-- =====================================================================
--  V9__identite_entreprise.sql
--  Migration Flyway — EF-CFG-01 (réécrit 2026-07-24) : identité de l'entreprise, réutilisée sur
--  les documents RH générés (certificats, cf. EF-DOC-04/EF-DOC-10 — T4.A1, pas encore implémenté).
--  Table à une seule ligne logique (imposé côté service, pas de contrainte SQL dédiée — même
--  esprit que horaires_reference/jours_feries : gestion Admin, pas de reprise de données réelle).
-- =====================================================================

CREATE TABLE identite_entreprise (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    raison_sociale  VARCHAR(255),
    adresse         TEXT,
    telephone       VARCHAR(50),
    email           VARCHAR(255),
    logo_fichier_id UUID REFERENCES fichiers(id),
    modifie_par     UUID REFERENCES utilisateurs(id),
    modifie_le      TIMESTAMPTZ NOT NULL DEFAULT now()
);
