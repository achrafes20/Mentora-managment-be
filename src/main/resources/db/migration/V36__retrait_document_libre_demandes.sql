-- EF-ADM-09 : "document libre" est un envoi Admin -> employe (module document, deja livre en
-- T4.A1), pas une demande employe soumise a approbation. Le type document_libre sur
-- demandes_administratives etait un doublon jamais vraiment exploitable (le formulaire de creation
-- ne permettait pas d'y attacher de fichier) — retire au profit du seul flux document.
DELETE FROM demandes_administratives WHERE type_demande = 'document_libre';

-- Les CHECK existants castent des litteraux vers type_demande_administrative par son nom : ils
-- doivent etre supprimes avant le renommage de l'ancien type, sinon Postgres compare l'ancien et
-- le nouveau type (deux types distincts pour lui) et refuse l'ALTER COLUMN.
ALTER TABLE demandes_administratives DROP CONSTRAINT chk_bon_sortie_horaires;
ALTER TABLE demandes_administratives DROP CONSTRAINT chk_conge_granularite;

ALTER TYPE type_demande_administrative RENAME TO type_demande_administrative_old;
CREATE TYPE type_demande_administrative AS ENUM (
    'conge', 'bon_sortie', 'autre',
    'conge_mariage', 'conge_naissance', 'conge_deces', 'conge_maladie'
);
ALTER TABLE demandes_administratives
    ALTER COLUMN type_demande TYPE type_demande_administrative
    USING type_demande::text::type_demande_administrative;
DROP TYPE type_demande_administrative_old;

ALTER TABLE demandes_administratives
    ADD CONSTRAINT chk_bon_sortie_horaires
        CHECK (type_demande <> 'bon_sortie' OR heure_depart IS NOT NULL AND heure_retour_prevue IS NOT NULL);
ALTER TABLE demandes_administratives
    ADD CONSTRAINT chk_conge_granularite
        CHECK (type_demande <> 'conge' OR granularite IS NOT NULL);

-- fichier_document_libre_id ne servait deja plus qu'au justificatif de conge_maladie (EF-ADM-14) ;
-- renomme (colonne + FK) pour ne plus referencer un type qui n'existe plus.
ALTER TABLE demandes_administratives
    RENAME COLUMN fichier_document_libre_id TO fichier_justificatif_id;
ALTER TABLE demandes_administratives
    RENAME CONSTRAINT demandes_administratives_fichier_document_libre_id_fkey
    TO demandes_administratives_fichier_justificatif_id_fkey;
