package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/** EF-ATT-06 : traçabilité de la correction manuelle d'un pointage par un Admin (NFR-SEC-03). */
record PointageCorrigeEvent(
    UUID pointageId, Instant ancienHorodatage, Instant nouvelHorodatage, String motif)
    implements EvenementMetier {

  @Override
  public String action() {
    return "pointage.corrige_manuellement";
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.presence;
  }

  @Override
  public String entiteType() {
    return "pointage";
  }

  @Override
  public UUID entiteId() {
    return pointageId;
  }

  @Override
  public Map<String, Object> details() {
    return Map.of(
        "ancienHorodatage", ancienHorodatage.toString(),
        "nouvelHorodatage", nouvelHorodatage.toString(),
        "motif", motif);
  }
}
