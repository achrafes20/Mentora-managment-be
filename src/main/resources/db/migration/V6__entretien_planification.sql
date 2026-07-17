-- EF-REC-08/09 (T3.B1, ajustement post-vérification manuelle) : `date_entretien` devient la date/
-- heure PLANIFIÉE de l'entretien (saisie par l'Admin à la programmation, modifiable tant qu'aucun
-- résultat n'a été rendu) ; `date_resultat` (nouvelle colonne) horodate le moment où le Manager
-- soumet réellement son résultat. Les deux étaient auparavant confondues dans `date_entretien`.
ALTER TABLE entretiens
    ADD COLUMN date_resultat TIMESTAMPTZ;
