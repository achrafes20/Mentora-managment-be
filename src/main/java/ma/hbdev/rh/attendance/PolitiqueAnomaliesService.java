package ma.hbdev.rh.attendance;

import java.time.Instant;
import java.util.Optional;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** EF-ATT-11 : seuil d'anomalies — table à une seule ligne logique (même principe que T4.B2). */
@Service
@Transactional
class PolitiqueAnomaliesService {

  private final PolitiqueAnomaliesRepository repository;
  private final ApplicationEventPublisher evenements;

  PolitiqueAnomaliesService(
      PolitiqueAnomaliesRepository repository, ApplicationEventPublisher evenements) {
    this.repository = repository;
    this.evenements = evenements;
  }

  @Transactional(readOnly = true)
  PolitiqueAnomaliesReponse obtenir() {
    return ligneUnique()
        .map(PolitiqueAnomaliesReponse::depuis)
        .orElse(new PolitiqueAnomaliesReponse(null, 3, 30, null, null));
  }

  PolitiqueAnomaliesReponse modifier(PolitiqueAnomaliesRequete requete) {
    PolitiqueAnomalies politique = ligneUnique().orElseGet(PolitiqueAnomalies::new);
    politique.setSeuilAnomalies(requete.seuilAnomalies());
    politique.setPeriodeJours(requete.periodeJours());
    politique.setModifiePar(CurrentUser.id().orElse(null));
    politique.setModifieLe(Instant.now());
    PolitiqueAnomalies sauvegardee = repository.save(politique);
    evenements.publishEvent(new PolitiqueAnomaliesModifieeEvent(sauvegardee.getId()));
    return PolitiqueAnomaliesReponse.depuis(sauvegardee);
  }

  private Optional<PolitiqueAnomalies> ligneUnique() {
    return repository.findAll().stream().findFirst();
  }
}
