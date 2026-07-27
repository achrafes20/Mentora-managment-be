package ma.hbdev.rh.shared.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.hbdev.rh.shared.event.ModuleAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EF-CFG-04/06 : consultation en lecture seule du journal d'audit, exposée aux autres modules (ici,
 * l'écran admin de {@code config}) sans leur donner accès à l'entité {@link JournalAudit} elle-même
 * — {@code shared/audit} en reste le seul propriétaire.
 */
@Service
@RequiredArgsConstructor
public class AuditConsultationService {

  private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
  private static final int TAILLE_PAGE_MAX = 200;
  // EF-CFG-05 : plafond de sécurité pour l'export (pas de pagination utilisateur ici), assez large
  // pour un usage réel de contrôle interne sans risquer un export sans fin.
  private static final int TAILLE_EXPORT_MAX = 5000;

  private final JournalAuditRepository repository;

  @Transactional(readOnly = true)
  public Page<JournalAuditReponse> rechercher(
      ModuleAudit module,
      UUID utilisateurId,
      LocalDate debut,
      LocalDate fin,
      String terme,
      int page,
      int size) {
    int tailleSecurisee = Math.max(1, Math.min(size, TAILLE_PAGE_MAX));
    return rechercher(
        module, utilisateurId, debut, fin, terme, Math.max(0, page), tailleSecurisee, true);
  }

  /**
   * EF-CFG-05 : export — mêmes filtres que {@link #rechercher}, sans le plafond de pagination UI.
   */
  @Transactional(readOnly = true)
  public List<JournalAuditReponse> rechercherPourExport(
      ModuleAudit module, UUID utilisateurId, LocalDate debut, LocalDate fin, String terme) {
    return rechercher(module, utilisateurId, debut, fin, terme, 0, TAILLE_EXPORT_MAX, false)
        .getContent();
  }

  private Page<JournalAuditReponse> rechercher(
      ModuleAudit module,
      UUID utilisateurId,
      LocalDate debut,
      LocalDate fin,
      String terme,
      int page,
      int taille,
      boolean plafonnerAuMaxUi) {
    Instant debutInstant = debut == null ? null : debut.atStartOfDay(ZONE).toInstant();
    // Borne exclusive : le lendemain minuit, pour inclure toute la journée de fin
    // (Africa/Casablanca).
    Instant finInstant = fin == null ? null : fin.plusDays(1).atStartOfDay(ZONE).toInstant();
    String termeNettoye = (terme == null || terme.isBlank()) ? null : terme.trim();
    int tailleSecurisee =
        plafonnerAuMaxUi ? Math.min(taille, TAILLE_PAGE_MAX) : Math.min(taille, TAILLE_EXPORT_MAX);

    Page<JournalAudit> resultats =
        repository.rechercher(
            module == null ? null : module.name(),
            utilisateurId,
            debutInstant,
            finInstant,
            termeNettoye,
            PageRequest.of(Math.max(0, page), Math.max(1, tailleSecurisee)));
    return resultats.map(JournalAuditReponse::depuis);
  }
}
