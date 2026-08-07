package ma.hbdev.rh.administrative;

enum TypeDemandeAdministrative {
  conge,
  bon_sortie,
  document_libre,
  autre,
  // EF-ADM-14 : congés légaux spéciaux — hors quota de congé payé (jamais de mouvement sur le
  // solde), pas de vérification de période de blocage (exceptionnels par nature).
  conge_mariage,
  conge_naissance,
  conge_deces,
  conge_maladie
}
