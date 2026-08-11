-- EF-ATT-19 : lien de révocation à usage unique dans l'e-mail du code personnel — permet à
-- l'employé de révoquer lui-même un téléphone perdu, sans authentification (le jeton lui-même est
-- la preuve d'intention, comme un lien de désinscription).
ALTER TABLE kiosque_activations ADD COLUMN jeton_revocation_hash VARCHAR(255);
