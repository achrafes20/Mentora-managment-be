package ma.hbdev.rh.shared.file;

/** Miroir Java du type Postgres {@code statut_antivirus_fichier}. Jamais traité (pas de scan). */
enum StatutAntivirusFichier {
  en_attente,
  propre,
  rejete
}
