package ma.hbdev.rh.employee;

class PhotoEmployeIntrouvableException extends RuntimeException {
  PhotoEmployeIntrouvableException() {
    super("Aucune photo pour cet employé");
  }
}
