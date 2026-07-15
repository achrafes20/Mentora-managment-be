package ma.hbdev.rh.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des horaires de référence de l'entreprise (EF-ATT-07). */
@Service
@Transactional
public class HoraireReferenceService {

  private final HoraireReferenceRepository repository;

  HoraireReferenceService(HoraireReferenceRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<HoraireReference> listerTous() {
    return repository.findAllByOrderByDateEffetDesc();
  }

  /** Retourne l'horaire en vigueur à la date donnée (null si aucun horaire encore saisi). */
  @Transactional(readOnly = true)
  public HoraireReference trouverEnVigueurA(LocalDate date) {
    return repository.findEnVigueurA(date).orElse(null);
  }

  /**
   * Crée un nouvel horaire de référence. Chaque nouvelle ligne devient l'horaire actif dès sa
   * date_effet.
   */
  public HoraireReference creer(HoraireReferenceRequete requete) {
    UUID creePar = CurrentUser.id().orElse(null);
    return repository.save(
        new HoraireReference(
            requete.heureDebutMatin(),
            requete.heureFinMatin(),
            requete.heureDebutApresMidi(),
            requete.heureFinApresMidi(),
            requete.toleranceMinutes(),
            requete.dateEffet(),
            creePar));
  }
}
