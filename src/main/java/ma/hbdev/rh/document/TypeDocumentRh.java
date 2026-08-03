package ma.hbdev.rh.document;

enum TypeDocumentRh {
  certificat_stage,
  certificat_travail,
  // Attestation d'emploi en cours (employé toujours actif) — distincte de certificat_travail,
  // qui documente un départ (EF-DOC-10, date de fin incluse).
  attestation_travail,
  document_libre,
  email_rejet_candidature
}
