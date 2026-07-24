-- =====================================================================
--  V10__periodes_blocage_conges.sql
--  Migration Flyway — EF-ADM-12 : périodes durant lesquelles aucune nouvelle demande de congé
--  ne peut être créée (clôture de fin d'année, période de forte activité...). Même principe de
--  gestion que jours_feries (EF-ADM-10) : Admin uniquement, pas de dépendance externe.
-- =====================================================================

CREATE TABLE periodes_blocage_conges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date_debut      DATE NOT NULL,
    date_fin        DATE NOT NULL,
    libelle         VARCHAR(150) NOT NULL,
    cree_par        UUID REFERENCES utilisateurs(id),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (date_fin >= date_debut)
);
CREATE INDEX idx_periodes_blocage_conges_dates ON periodes_blocage_conges(date_debut, date_fin);
