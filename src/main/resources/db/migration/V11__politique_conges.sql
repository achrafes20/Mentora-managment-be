-- =====================================================================
--  V11__politique_conges.sql
--  Migration Flyway — EF-ADM-11 : taux d'acquisition mensuel de congés, configurable par type de
--  contrat (remplace la constante ACQUISITION_MENSUELLE fixée dans AdministrativeService).
--  Valeurs par défaut identiques au comportement précédent (1,5 j/mois CDI/CDD, 0 stagiaires) —
--  reprendre ces valeurs en seed ne change rien tant qu'un Admin ne les modifie pas explicitement.
-- =====================================================================

CREATE TABLE politique_conges (
    type_contrat    type_contrat_employe PRIMARY KEY,
    jours_par_mois  NUMERIC(4,2) NOT NULL CHECK (jours_par_mois >= 0),
    modifie_par     UUID REFERENCES utilisateurs(id),
    modifie_le      TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO politique_conges (type_contrat, jours_par_mois) VALUES
    ('CDI', 1.5),
    ('CDD', 1.5),
    ('STAGIAIRE', 0),
    ('STAGIAIRE_REMUNERE', 0);
