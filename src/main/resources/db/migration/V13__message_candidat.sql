-- =====================================================================
--  V13__message_candidat.sql
--  Migration Flyway — le corps de l'e-mail de candidature (au-delà du CV en pièce jointe) était
--  déjà transmis par le workflow n8n d'ingestion (paramètre "corps") mais jamais persisté, donc
--  invisible côté Admin/Manager — un candidat peut y écrire des informations utiles (disponibilité,
--  motivation, contexte) absentes du CV. Colonne nullable : rien à reprendre pour les candidatures
--  déjà ingérées avant cette migration (le corps d'origine n'a jamais été stocké).
-- =====================================================================

ALTER TABLE candidatures ADD COLUMN message_candidat TEXT;
