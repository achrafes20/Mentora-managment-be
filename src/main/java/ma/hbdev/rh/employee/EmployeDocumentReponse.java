package ma.hbdev.rh.employee;

import java.time.Instant;
import java.util.UUID;
import ma.hbdev.rh.shared.file.FichierUploade;

public record EmployeDocumentReponse(
    UUID id,
    UUID fichierId,
    String nomOriginal,
    String typeMime,
    String typeDocument,
    Instant creeLe) {

  static EmployeDocumentReponse depuis(EmployeDocument document, FichierUploade fichier) {
    return new EmployeDocumentReponse(
        document.getId(),
        fichier.id(),
        fichier.nomOriginal(),
        fichier.typeMime(),
        document.getTypeDocument(),
        document.getCreeLe());
  }
}
