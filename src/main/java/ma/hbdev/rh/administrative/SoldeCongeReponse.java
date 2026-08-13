package ma.hbdev.rh.administrative;

import java.math.BigDecimal;
import java.util.UUID;

record SoldeCongeReponse(
    UUID employeId,
    String employeNomComplet,
    BigDecimal acquisJours,
    BigDecimal mouvementsJours,
    BigDecimal soldeJours) {}
