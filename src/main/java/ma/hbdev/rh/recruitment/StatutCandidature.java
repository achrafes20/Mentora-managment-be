package ma.hbdev.rh.recruitment;

/** Miroir Java du type Postgres {@code statut_candidature} — pipeline EF-REC-07/11/12. */
enum StatutCandidature {
  recu,
  preselectionne,
  entretien,
  decision,
  embauche,
  rejete,
  en_attente,
  suggestion_reactivation,
  archivee,
  non_traite
}
