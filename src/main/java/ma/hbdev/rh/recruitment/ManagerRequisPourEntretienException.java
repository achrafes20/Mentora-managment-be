package ma.hbdev.rh.recruitment;

/**
 * EF-REC-08 : un manager est obligatoire pour planifier un entretien — sans lui, aucune ligne
 * {@code entretiens} ne peut être créée (colonne {@code manager_id} NOT NULL), ce qui laisserait la
 * candidature bloquée en statut "Entretien" sans jamais apparaître dans la file d'un Manager.
 */
class ManagerRequisPourEntretienException extends RuntimeException {

  ManagerRequisPourEntretienException() {
    super("Un manager doit être assigné pour planifier un entretien");
  }
}
