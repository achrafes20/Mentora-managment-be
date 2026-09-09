package ma.hbdev.rh.administrative;

enum TypeDemandeAdministrative {
  conge,
  bon_sortie,
  autre,
  // EF-ADM-14 : congés légaux spéciaux — hors quota de congé payé (jamais de mouvement sur le
  // solde), pas de vérification de période de blocage (exceptionnels par nature).
  conge_mariage,
  conge_naissance,
  conge_deces,
  conge_maladie,
  // Simple ticket (motif libre obligatoire, même validation que "autre") signalant à l'Admin
  // RH qu'un document est attendu — ex. "attestation de travail pour la banque". Aucune
  // automatisation : l'Admin génère/envoie toujours le document depuis Documents RH
  // (DocumentRhService), cette demande n'est qu'un point d'entrée centralisé, pas un
  // déclencheur direct de génération.
  demande_document
}
