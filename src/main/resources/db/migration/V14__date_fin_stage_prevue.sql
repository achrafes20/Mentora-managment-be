-- EF-DOC-12 : la surveillance de fin de stage (SurveillancePlanifieeService) lisait
-- date_fin_contrat_prevue pour les STAGIAIRE alors que ce champ est réservé aux CDD
-- (chk_fin_contrat_cdd_uniquement) — la branche stage ne pouvait donc jamais se déclencher.
-- Champ dédié, symétrique de date_fin_contrat_prevue mais réservé aux stagiaires.
ALTER TABLE employes ADD COLUMN date_fin_stage_prevue DATE;

ALTER TABLE employes ADD CONSTRAINT chk_fin_stage_stagiaire_uniquement
    CHECK (type_contrat IN ('STAGIAIRE', 'STAGIAIRE_REMUNERE') OR date_fin_stage_prevue IS NULL);

CREATE INDEX idx_employes_fin_stage ON employes(date_fin_stage_prevue)
    WHERE type_contrat IN ('STAGIAIRE', 'STAGIAIRE_REMUNERE');
