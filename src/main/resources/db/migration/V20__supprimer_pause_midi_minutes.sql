-- EF-ATT-03/EF-ATT-07 : retour sur la colonne pause_midi_minutes ajoutée en V19. La pause déjeuner
-- est en fait déjà entièrement définie par l'écart entre heure_fin_matin et
-- heure_debut_apres_midi de l'horaire de référence — un champ séparé pouvait se contredire avec
-- ces deux heures (ex. 13h/14h saisis mais une pause à 30 min dans l'autre champ). La durée de
-- pause est désormais calculée dynamiquement côté application (PointageService#pauseMidiA).
ALTER TABLE horaires_reference
    DROP COLUMN pause_midi_minutes;
