-- Sujet de stage (EF-EMP-01) : optionnel, saisi à la création pour les employés en type de contrat
-- STAGIAIRE/STAGIAIRE_REMUNERE. Nullable : aucune reprise de données réelle par migration, et sans
-- objet pour les autres types de contrat.
ALTER TABLE employes ADD COLUMN sujet_stage VARCHAR(255);
