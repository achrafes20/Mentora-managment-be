-- =====================================================================
--  AJOUT — TÉLÉTRAVAIL HYBRIDE (EF-ATT-08 → EF-ATT-10)
--  Deux blocs à insérer dans schema_v1.sql :
--    1) le type énuméré, à ajouter en SECTION 0 (avec les autres CREATE TYPE)
--    2) les deux tables, à ajouter en SECTION 5, juste après jours_feries
--  Aucune table existante n'est modifiée. Migration additive pure —
--  correspond à une future V3__telework.sql si les migrations sont
--  livrées de façon incrémentale.
-- =====================================================================


-- --- 1) À ajouter en SECTION 0 (TYPES ÉNUMÉRÉS), avec les autres CREATE TYPE ---

CREATE TYPE type_jour_semaine AS ENUM
    ('lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche');


-- --- 2) À ajouter en SECTION 5 (PRÉSENCE), juste après jours_feries ---

-- EF-ATT-08 : planning de télétravail récurrent par employé, saisi manuellement
-- par l'Admin RH. Historisé comme employe_transferts (§2.3/§2.12) : un employé
-- peut avoir plusieurs plannings successifs, l'ancien est conservé pour l'audit
-- plutôt qu'écrasé. La contrainte "au plus un planning actif à un instant T"
-- est portée par la couche service, pas par une exclusion SQL (§2.12).
CREATE TABLE plannings_teletravail (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id      UUID NOT NULL REFERENCES employes(id) ON DELETE CASCADE,
    date_debut      DATE NOT NULL,
    date_fin        DATE,                                    -- NULL = durée indéterminée (EF-ATT-08)
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

-- EF-ATT-08 : détail des jours de la semaine télétravaillés pour un planning donné.
-- Une ligne par jour plutôt qu'un bitmask ou un tableau (§2.12) : la question
-- "cet employé est-il en télétravail ce jour-là ?" devient un simple EXISTS sur
-- index composite, sans manipulation bit à bit côté application.
CREATE TABLE plannings_teletravail_jours (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    planning_teletravail_id UUID NOT NULL REFERENCES plannings_teletravail(id) ON DELETE CASCADE,
    jour_semaine            type_jour_semaine NOT NULL,

    UNIQUE (planning_teletravail_id, jour_semaine)          -- un jour ne peut être ajouté deux fois au même planning
);
CREATE INDEX idx_plannings_teletravail_jours_lookup
    ON plannings_teletravail_jours(planning_teletravail_id, jour_semaine);


-- =====================================================================
--  Requête de référence — "cet employé est-il en télétravail à cette date ?"
--  Utilisée par la détection d'anomalies (anomalies_pointage) pour court-
--  circuiter EF-ATT-04 sur les jours télétravaillés (EF-ATT-09). Fournie ici
--  à titre de documentation ; l'implémentation réelle vit côté service.
-- =====================================================================
--
-- SELECT EXISTS (
--     SELECT 1
--     FROM plannings_teletravail pt
--     JOIN plannings_teletravail_jours ptj ON ptj.planning_teletravail_id = pt.id
--     WHERE pt.employe_id = :employe_id
--       AND :date_a_verifier BETWEEN pt.date_debut AND COALESCE(pt.date_fin, :date_a_verifier)
--       AND ptj.jour_semaine = :jour_semaine_de_la_date
-- );
