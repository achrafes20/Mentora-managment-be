-- EF-ATT-04 : nouveau type d'anomalie "absence_totale" — un employé actif, jour ouvré, sans aucun
-- pointage (ni entrée ni sortie), qui n'est ni en télétravail planifié ni en congé approuvé ce
-- jour-là. Contrairement à l'ex-"presence_incomplete" (retirée en V23, jamais générée en
-- pratique car le moteur ne balayait que les employés ayant déjà un pointage), celle-ci couvre le
-- vrai cas manquant : une absence complète et injustifiée ne déclenchait jusqu'ici aucune alerte.
ALTER TYPE type_anomalie_pointage ADD VALUE 'absence_totale';
