-- EF-DOC-14 : attestation de salaire générée depuis la fiche employé (même mécanisme que les
-- certificats existants) — nécessite un salaire brut mensuel sur la fiche, absent jusqu'ici.
ALTER TABLE employes ADD COLUMN salaire_brut_mensuel NUMERIC(10, 2);
ALTER TYPE type_document_rh ADD VALUE 'attestation_salaire';
