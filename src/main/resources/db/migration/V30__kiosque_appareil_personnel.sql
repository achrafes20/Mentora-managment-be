-- EF-ATT-16 : pointage par le téléphone de l'employé (scan de son propre badge) plutôt qu'un
-- écran de kiosque partagé — plus réaliste en multi-sites. Réutilise le mécanisme d'activation
-- d'appareil existant (code + jeton, verrouillage, expiration) : employe_id NULL = kiosque partagé
-- classique (comportement inchangé), renseigné = appareil personnel lié à cet employé.
ALTER TABLE kiosque_activations ADD COLUMN employe_id UUID REFERENCES employes(id);
