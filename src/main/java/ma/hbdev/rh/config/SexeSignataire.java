package ma.hbdev.rh.config;

/**
 * Miroir Java du type Postgres {@code sexe_signataire}. Distinct de {@code employee.SexeEmploye}
 * bien que les valeurs soient identiques — un module ne partage jamais un type Postgres avec un
 * autre (ai-instructions.md règle 2/6).
 */
enum SexeSignataire {
  HOMME,
  FEMME
}
