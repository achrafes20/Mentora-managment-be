package ma.hbdev.rh.auth;

import java.time.Instant;

/** Accompagne un 401 "compte verrouillé" — permet au frontend d'afficher un décompte précis. */
record VerrouillageReponse(Instant verrouilleJusquA) {}
