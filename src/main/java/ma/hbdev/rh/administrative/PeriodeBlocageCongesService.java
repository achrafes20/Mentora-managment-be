package ma.hbdev.rh.administrative;

import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des périodes de blocage de congés (EF-ADM-12), extrait d'{@code AdministrativeService} —
 * CRUD Admin-only autonome. Le contrôle de chevauchement lui-même, appliqué à la création d'une
 * demande de congé, reste dans {@code AdministrativeService#validerConge} via {@link
 * PeriodeBlocageCongesRepository} injecté directement là-bas (simple requête, pas d'orchestration).
 */
@Service
@Transactional
class PeriodeBlocageCongesService {

  private final PeriodeBlocageCongesRepository repository;
  private final ApplicationEventPublisher evenements;

  PeriodeBlocageCongesService(
      PeriodeBlocageCongesRepository repository, ApplicationEventPublisher evenements) {
    this.repository = repository;
    this.evenements = evenements;
  }

  @Transactional(readOnly = true)
  List<PeriodeBlocageCongesReponse> lister() {
    return repository.findAllByOrderByDateDebutDesc().stream()
        .map(PeriodeBlocageCongesReponse::depuis)
        .toList();
  }

  PeriodeBlocageCongesReponse creer(PeriodeBlocageCongesRequete requete) {
    verifierAdmin();
    if (requete.dateFin().isBefore(requete.dateDebut())) {
      throw new IllegalArgumentException("La date de fin doit etre apres la date de debut");
    }
    PeriodeBlocageConges periode =
        repository.save(
            new PeriodeBlocageConges(
                requete.dateDebut(), requete.dateFin(), requete.libelle(), utilisateurCourant()));
    evenements.publishEvent(new PeriodeBlocageCongesEvent(periode.getId(), "creation"));
    return PeriodeBlocageCongesReponse.depuis(periode);
  }

  void supprimer(UUID id) {
    verifierAdmin();
    repository.deleteById(id);
    evenements.publishEvent(new PeriodeBlocageCongesEvent(id, "suppression"));
  }

  private void verifierAdmin() {
    if (!CurrentUser.hasRole("ADMIN")) {
      throw new AccessDeniedException("Action reservee admin");
    }
  }

  private UUID utilisateurCourant() {
    return CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifie"));
  }
}
