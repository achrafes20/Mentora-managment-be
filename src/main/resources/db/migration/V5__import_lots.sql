-- EF-EMP-07 : import en masse Excel/CSV (départements, employés, soldes de congés initiaux).
-- Historique des lots d'import (dry-run et réel) + rapport ligne par ligne, pour l'écran
-- "journal des imports" et pour pouvoir revoir le détail d'un import passé.

CREATE TYPE cible_import        AS ENUM ('DEPARTEMENTS', 'EMPLOYES', 'SOLDES_CONGES_INITIAUX');
CREATE TYPE mode_import_lot     AS ENUM ('SIMULATION', 'REEL');
CREATE TYPE statut_ligne_import AS ENUM ('VALIDE', 'AVERTISSEMENT', 'ERREUR');
CREATE TYPE action_ligne_import AS ENUM ('CREATION', 'MISE_A_JOUR', 'AUCUN_CHANGEMENT', 'IGNOREE');

CREATE TABLE imports_lots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cible               cible_import NOT NULL,
    mode                mode_import_lot NOT NULL,
    nom_fichier         VARCHAR(255) NOT NULL,
    nb_lignes_total     INTEGER NOT NULL,
    nb_lignes_valides   INTEGER NOT NULL,
    nb_lignes_erreur    INTEGER NOT NULL,
    execute_par         UUID REFERENCES utilisateurs(id),
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_imports_lots_cible ON imports_lots(cible, cree_le);

CREATE TABLE imports_lignes (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lot_id              UUID NOT NULL REFERENCES imports_lots(id) ON DELETE CASCADE,
    numero_ligne        INTEGER NOT NULL,
    statut              statut_ligne_import NOT NULL,
    action              action_ligne_import NOT NULL,
    donnees_brutes      JSONB NOT NULL,
    erreurs             TEXT,
    entite_id           UUID
);
CREATE INDEX idx_imports_lignes_lot ON imports_lignes(lot_id, numero_ligne);
