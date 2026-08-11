-- EF-EMP-14 : conformité RH Maroc — identifiants organismes sociaux, RIB et fin de période
-- d'essai sur la fiche employé. Tous nullable : non connus/applicables pour tous les profils
-- (ex. STAGIAIRE non rémunéré sans CNSS), pas de reprise de données existantes.
alter table employes
  add column numero_cnss varchar(50),
  add column numero_amo varchar(50),
  add column numero_cimr varchar(50),
  add column rib varchar(34),
  add column periode_essai_fin_le date;
