-- EF-ATT-03/EF-ATT-07 : durée de pause déjeuner configurable par horaire de référence, au lieu de
-- la valeur d'1h auparavant codée en dur dans PointageService. Défaut à 60 pour préserver le
-- comportement existant sur les horaires déjà enregistrés.
ALTER TABLE horaires_reference
    ADD COLUMN pause_midi_minutes INT NOT NULL DEFAULT 60;
