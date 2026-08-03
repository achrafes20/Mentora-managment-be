package ma.hbdev.rh.employee;

/**
 * Miroir Java du type Postgres {@code sexe_employe}. Utilisé pour l'accord de genre sur les
 * certificats générés (document.CertificatGenerator) — optionnel, aucune valeur par défaut imposée
 * pour les fiches existantes (cf. V16__sexe_employe_et_signataire.sql).
 */
enum SexeEmploye {
  HOMME,
  FEMME
}
