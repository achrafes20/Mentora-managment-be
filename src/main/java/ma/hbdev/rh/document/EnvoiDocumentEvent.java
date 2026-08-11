package ma.hbdev.rh.document;

import java.util.Map;
import java.util.UUID;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.event.ModuleAudit;

/**
 * NFR-SEC-03 : traçabilité de l'envoi effectif d'un document RH (certificat de stage/travail,
 * document libre) — un document nominatif contenant des données personnelles, envoyé par e-mail
 * vers l'extérieur. Le module {@code document} ne publiait jusqu'ici aucun événement métier ; ni la
 * génération d'un certificat, ni un renvoi déclenché par la surveillance planifiée (T4.A1)
 * n'apparaissaient dans journal_audit.
 */
record EnvoiDocumentEvent(
    UUID envoiId, UUID employeId, String typeDocument, String employeNomComplet)
    implements EvenementMetier {

  @Override
  public String action() {
    return "envoi";
  }

  @Override
  public ModuleAudit module() {
    return ModuleAudit.document;
  }

  @Override
  public String entiteType() {
    return "envoi_document";
  }

  @Override
  public UUID entiteId() {
    return envoiId;
  }

  @Override
  public Map<String, Object> details() {
    return Map.of("typeDocument", typeDocument, "employe", employeNomComplet);
  }
}
