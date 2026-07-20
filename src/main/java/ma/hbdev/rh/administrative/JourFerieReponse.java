package ma.hbdev.rh.administrative;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record JourFerieReponse(
    UUID id, LocalDate dateFerie, String libelle, UUID gerePar, Instant creeLe) {
  static JourFerieReponse depuis(JourFerie jour) {
    return new JourFerieReponse(
        jour.getId(), jour.getDateFerie(), jour.getLibelle(), jour.getGerePar(), jour.getCreeLe());
  }
}
