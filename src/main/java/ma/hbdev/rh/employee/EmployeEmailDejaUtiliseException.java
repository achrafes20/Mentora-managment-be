package ma.hbdev.rh.employee;

class EmployeEmailDejaUtiliseException extends RuntimeException {

  EmployeEmailDejaUtiliseException(String email) {
    super("Un employé existe déjà avec cet e-mail : " + email);
  }
}
