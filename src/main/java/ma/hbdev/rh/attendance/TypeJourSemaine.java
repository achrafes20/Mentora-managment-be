package ma.hbdev.rh.attendance;

import java.time.DayOfWeek;

/** Miroir Java du type Postgres {@code type_jour_semaine}. */
enum TypeJourSemaine {
  lundi,
  mardi,
  mercredi,
  jeudi,
  vendredi,
  samedi,
  dimanche;

  /**
   * {@link DayOfWeek#name()} est en anglais ("MONDAY") — jamais convertible via {@code
   * valueOf(name().toLowerCase())} vers ces constantes françaises. {@link DayOfWeek#getValue()}
   * (1=lundi..7=dimanche) correspond exactement à l'ordre de déclaration ci-dessus.
   */
  static TypeJourSemaine depuis(DayOfWeek jour) {
    return values()[jour.getValue() - 1];
  }
}
