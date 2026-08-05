-- EF-EMP-XX : désactivation automatique des employés dont le contrat/stage est arrivé à échéance.
-- Un CDD expiré se désactive avec le motif fin_cdd déjà existant ; un stage expiré n'avait pas de
-- motif dédié (aurait fallu détourner "autre") — ajout de 'fin_stage' pour rester cohérent avec le
-- même principe.
ALTER TYPE motif_depart_employe ADD VALUE 'fin_stage';
