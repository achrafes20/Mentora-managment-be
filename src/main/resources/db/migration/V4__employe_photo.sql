-- Photo employé : référence vers la table fichiers (EF-EMP-09)
ALTER TABLE employes
    ADD COLUMN photo_fichier_id UUID REFERENCES fichiers(id);

-- L'ancienne colonne texte photo_url n'était pas utilisée par l'application
ALTER TABLE employes DROP COLUMN IF EXISTS photo_url;
