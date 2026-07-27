package ma.hbdev.rh.document;

import java.time.Instant;
import java.util.UUID;

record EnvoiDocumentResponse(
    UUID id,
    UUID employeId,
    TypeDocumentRh typeDocument,
    UUID fichierId,
    String destinataireEmail,
    Instant dateEnvoi,
    UUID envoyePar) {
  static EnvoiDocumentResponse depuis(EnvoiDocument envoi) {
    return new EnvoiDocumentResponse(
        envoi.getId(),
        envoi.getEmployeId(),
        envoi.getTypeDocument(),
        envoi.getFichierId(),
        envoi.getDestinataireEmail(),
        envoi.getDateEnvoi(),
        envoi.getEnvoyePar());
  }
}
