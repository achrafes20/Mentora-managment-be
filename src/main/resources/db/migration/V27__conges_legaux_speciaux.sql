-- EF-ADM-14 : congés légaux spéciaux (mariage, naissance, décès, maladie avec justificatif) en
-- plus du congé payé/bon de sortie existants.
ALTER TYPE type_demande_administrative ADD VALUE 'conge_mariage';
ALTER TYPE type_demande_administrative ADD VALUE 'conge_naissance';
ALTER TYPE type_demande_administrative ADD VALUE 'conge_deces';
ALTER TYPE type_demande_administrative ADD VALUE 'conge_maladie';
