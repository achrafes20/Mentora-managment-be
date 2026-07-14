package ma.hbdev.rh.employee;

class EmployeSansEmailException extends RuntimeException {
  EmployeSansEmailException() {
    super("L'employé n'a pas d'adresse e-mail renseignée");
  }
}
