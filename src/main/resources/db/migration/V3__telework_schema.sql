-- =====================================================================
--  V3__telework_schema.sql
--  Migration Flyway — Ajout du télétravail hybride (EF-ATT-08 → EF-ATT-10)
-- =====================================================================

CREATE TYPE type_jour_semaine AS ENUM
    ('lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche');

CREATE TABLE plannings_teletravail (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id      UUID NOT NULL REFERENCES employes(id) ON DELETE CASCADE,
    date_debut      DATE NOT NULL,
    date_fin        DATE,
    cree_par        UUID REFERENCES utilisateurs(id),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    modifie_le      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_planning_teletravail_dates
        CHECK (date_fin IS NULL OR date_fin >= date_debut)
);
CREATE INDEX idx_plannings_teletravail_employe ON plannings_teletravail(employe_id, date_debut, date_fin);

CREATE TRIGGER trg_plannings_teletravail_modifie_le
    BEFORE UPDATE ON plannings_teletravail
    FOR EACH ROW EXECUTE FUNCTION maj_horodatage_modification();

CREATE TABLE plannings_teletravail_jours (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    planning_teletravail_id UUID NOT NULL REFERENCES plannings_teletravail(id) ON DELETE CASCADE,
    jour_semaine            type_jour_semaine NOT NULL,

    UNIQUE (planning_teletravail_id, jour_semaine)
);
CREATE INDEX idx_plannings_teletravail_jours_lookup
    ON plannings_teletravail_jours(planning_teletravail_id, jour_semaine);
