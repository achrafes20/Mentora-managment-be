package ma.hbdev.rh.administrative;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record PolitiqueCongeReponse(
    String typeContrat, BigDecimal joursParMois, UUID modifiePar, Instant modifieLe) {}
