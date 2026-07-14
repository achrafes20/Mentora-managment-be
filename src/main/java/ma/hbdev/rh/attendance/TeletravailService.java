package ma.hbdev.rh.attendance;

import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des plannings de télétravail des employés (EF-ATT-08/10). */
@Service
@Transactional
public class TeletravailService {

  private final PlanningTeletravailRepository repository;

  TeletravailService(PlanningTeletravailRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<PlanningTeletravail> listerParEmploye(UUID employeId) {
    return repository.findByEmployeIdOrderByDateDebutDesc(employeId);
  }

  public PlanningTeletravail creer(UUID employeId, PlanningTeletravailRequete requete) {
    UUID creePar = CurrentUser.id().orElse(null);
    PlanningTeletravail planning =
        repository.save(
            new PlanningTeletravail(employeId, requete.dateDebut(), requete.dateFin(), creePar));
    // Flush pour obtenir l'ID généré avant d'appeler setJours
    repository.flush();
    planning.setJours(requete.jours());
    return planning;
  }

  public void supprimer(UUID planningId) {
    PlanningTeletravail planning =
        repository
            .findById(planningId)
            .orElseThrow(() -> new PlanningTeletravailIntrouvableException(planningId));
    repository.delete(planning);
  }
}
