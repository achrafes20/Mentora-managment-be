package ma.hbdev.rh.employee;

import java.math.BigDecimal;

/**
 * EF-DOC-14 — modification ciblée du salaire brut mensuel, Admin uniquement (donnée sensible, hors
 * formulaire fiche standard).
 */
public record SalaireRequete(BigDecimal salaireBrutMensuel) {}
