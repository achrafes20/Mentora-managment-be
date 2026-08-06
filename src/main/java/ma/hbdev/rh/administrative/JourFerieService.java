package ma.hbdev.rh.administrative;

import java.util.List;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestion des jours fériés (EF-ADM-XX), extrait d'{@code AdministrativeService} — CRUD Admin-only
 * autonome, sans dépendance sur le cycle de vie des demandes/congés au-delà de la lecture directe
 * de {@link JourFerieRepository} par {@code AdministrativeService#duree} (repository injecté là où
 * il est utilisé, pas de va-et-vient inter-services pour une simple requête).
 */
@Service
@Transactional
class JourFerieService {

  private final JourFerieRepository repository;

  JourFerieService(JourFerieRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  List<JourFerieReponse> lister() {
    return repository.findAllByOrderByDateFerieDesc().stream()
        .map(JourFerieReponse::depuis)
        .toList();
  }

  JourFerieReponse creer(JourFerieRequete requete) {
    verifierAdmin();
    JourFerie jour =
        repository
            .findByDateFerie(requete.dateFerie())
            .map(
                existant -> {
                  existant.modifier(requete.libelle(), utilisateurCourant());
                  return existant;
                })
            .orElseGet(
                () -> new JourFerie(requete.dateFerie(), requete.libelle(), utilisateurCourant()));
    return JourFerieReponse.depuis(repository.save(jour));
  }

  void supprimer(java.util.UUID id) {
    verifierAdmin();
    repository.deleteById(id);
  }

  private void verifierAdmin() {
    if (!CurrentUser.hasRole("ADMIN")) {
      throw new AccessDeniedException("Action reservee admin");
    }
  }

  private java.util.UUID utilisateurCourant() {
    return CurrentUser.id().orElseThrow(() -> new AccessDeniedException("Non authentifie"));
  }
}
