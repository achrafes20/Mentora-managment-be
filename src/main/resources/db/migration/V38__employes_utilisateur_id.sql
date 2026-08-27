-- EF-EMP-18 : un Manager est aussi un employe. Lien formel entre le compte de connexion
-- (utilisateurs) et la fiche RH (employes) correspondante, pour que le systeme puisse retrouver
-- "quelle fiche RH correspond a ce compte connecte" plutot que de laisser les deux tables
-- totalement decorrelees comme avant. Nullable : les employes ordinaires n'ont pas de compte de
-- connexion, et les Managers/Admins crees avant ce changement n'ont pas de fiche liee
-- retroactivement (hors perimetre de cette migration).
--
-- Idempotent (IF NOT EXISTS / verification pg_constraint) : observe en developpement local sur ce
-- poste, un demarrage backend tue par Docker (healthcheck) exactement entre le commit de l'ALTER
-- et l'ecriture de la ligne flyway_schema_history laisse la colonne deja presente mais la
-- migration non enregistree comme reussie -- le redemarrage suivant relance alors ce script et un
-- simple ADD COLUMN echouerait avec "column already exists".
ALTER TABLE employes ADD COLUMN IF NOT EXISTS utilisateur_id UUID;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'employes_utilisateur_id_key'
  ) THEN
    ALTER TABLE employes ADD CONSTRAINT employes_utilisateur_id_key UNIQUE (utilisateur_id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'employes_utilisateur_id_fkey'
  ) THEN
    ALTER TABLE employes
      ADD CONSTRAINT employes_utilisateur_id_fkey
      FOREIGN KEY (utilisateur_id) REFERENCES utilisateurs(id) ON DELETE SET NULL;
  END IF;
END $$;
