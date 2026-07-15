package ma.hbdev.rh.attendance;

import java.util.UUID;

class PlanningTeletravailIntrouvableException extends RuntimeException {
  PlanningTeletravailIntrouvableException(UUID id) {
    super("Planning de télétravail introuvable : " + id);
  }
}
