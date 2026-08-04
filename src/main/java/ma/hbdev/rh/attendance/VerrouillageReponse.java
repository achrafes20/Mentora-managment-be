package ma.hbdev.rh.attendance;

import java.time.Instant;

/** Accompagne un 401 "code verrouillé" — permet au frontend d'afficher un décompte précis. */
public record VerrouillageReponse(Instant verrouilleJusquA) {}
