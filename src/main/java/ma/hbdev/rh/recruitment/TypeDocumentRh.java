package ma.hbdev.rh.recruitment;

/**
 * Miroir Java du type Postgres {@code type_document_rh}. Le module recrutement n'utilise que {@code
 * email_rejet_candidature} (EF-REC-14) ; les autres valeurs appartiennent au futur module {@code
 * document} (T4.A1) qui aura sa propre projection JPA de la table {@code envois_documents} — pas de
 * couplage entre les deux, chaque module ne mappe que ce dont il a besoin.
 */
enum TypeDocumentRh {
  certificat_stage,
  certificat_travail,
  document_libre,
  email_rejet_candidature
}
