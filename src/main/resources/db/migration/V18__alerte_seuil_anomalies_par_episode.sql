-- EF-ATT-11 : mémorise quelle anomalie a déclenché l'alerte de seuil, pour notifier une seule fois
-- par "épisode" d'anomalies non résolues (au lieu de comparer un compte instantané au seuil, qui
-- pouvait rater l'alerte si le compte sautait directement au-dessus du seuil sans jamais l'égaler
-- pile — ex. seuil abaissé par un Admin pendant qu'un employé a déjà des anomalies non résolues).
-- Une fois toutes les anomalies de l'épisode résolues, le filtre resolue=false exclut naturellement
-- cette ligne des vérifications futures : un nouvel épisode peut redéclencher une alerte.
ALTER TABLE anomalies_pointage
    ADD COLUMN a_declenche_alerte_seuil BOOLEAN NOT NULL DEFAULT FALSE;
