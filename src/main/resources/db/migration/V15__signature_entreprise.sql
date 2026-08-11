-- =====================================================================
--  V15__signature_entreprise.sql
--  Migration Flyway — EF-CFG-01 : ajoute l'image de signature/cachet de l'entreprise, réutilisée
--  sur les certificats générés (cf. document.CertificatGenerator) à la place de l'encart
--  "Signature et cachet" à remplir à la main quand elle est disponible.
--  Même pattern que logo_fichier_id (V9__identite_entreprise.sql).
-- =====================================================================

ALTER TABLE identite_entreprise
    ADD COLUMN signature_fichier_id UUID REFERENCES fichiers(id);
