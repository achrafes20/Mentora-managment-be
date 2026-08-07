-- EF-REC-14 : vivier/famille de poste sur les offres d'emploi (enseignants/pédagogues, ingénieurs
-- IA, designers, stagiaires...) — varchar libre, pas d'enum Postgres natif (liste métier évolutive).
ALTER TABLE offres_emploi ADD COLUMN categorie VARCHAR(50);
