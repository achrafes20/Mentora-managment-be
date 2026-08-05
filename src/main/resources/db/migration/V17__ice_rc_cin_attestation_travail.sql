-- =====================================================================
--  V17__ice_rc_cin_attestation_travail.sql
--  Migration Flyway — nouveau document "attestation de travail" (employé encore actif, distinct
--  du certificat de travail existant qui documente un départ, cf. EF-DOC-10). Ajoute les champs
--  d'identification légale marocaine nécessaires à son en-tête/corps.
-- =====================================================================

-- EF-CFG-01 : identifiants légaux de l'entreprise, réutilisés en en-tête des documents générés.
ALTER TABLE identite_entreprise
    ADD COLUMN ice   VARCHAR(50),
    ADD COLUMN rc    VARCHAR(50),
    ADD COLUMN ville VARCHAR(255);

-- CIN de l'employé, affichée sur l'attestation de travail. Nullable : aucune reprise de données
-- réelle par migration, à compléter par l'Admin RH sur la fiche employé.
ALTER TABLE employes ADD COLUMN cin VARCHAR(50);

-- Nouveau type de document généré (document.CertificatGenerator/DocumentRhService), à côté de
-- certificat_stage/certificat_travail déjà existants dans envois_documents.
ALTER TYPE type_document_rh ADD VALUE 'attestation_travail';
