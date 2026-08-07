package ma.hbdev.rh.document;

enum TypeDocumentRh {
  certificat_stage,
  certificat_travail,
  // Attestation d'emploi en cours (employé toujours actif) — distincte de certificat_travail,
  // qui documente un départ (EF-DOC-10, date de fin incluse).
  attestation_travail,
  // EF-DOC-14 : attestation de salaire — même éligibilité (actif, CDI/CDD) que attestation_travail.
  attestation_salaire,
  document_libre,
  email_rejet_candidature
}
