package ma.hbdev.rh.employee;

class DepartementNomDejaUtiliseException extends RuntimeException {

  DepartementNomDejaUtiliseException(String nom) {
    super("Un département existe déjà avec ce nom : " + nom);
  }
}
