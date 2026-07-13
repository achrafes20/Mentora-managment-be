package ma.hbdev.rh.employee;

import java.util.UUID;

class EmployeDocumentIntrouvableException extends RuntimeException {

  EmployeDocumentIntrouvableException(UUID documentId) {
    super("Document introuvable : " + documentId);
  }
}
