package ma.hbdev.rh.shared.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    Instant debutInstant = debut == null ? null : debut.atStartOfDay(ZONE).toInstant();
    // Borne exclusive : le lendemain minuit, pour inclure toute la journée de fin
    // (Africa/Casablanca).
    Instant finInstant = fin == null ? null : fin.plusDays(1).atStartOfDay(ZONE).toInstant();
    String termeNettoye = (terme == null || terme.isBlank()) ? null : terme.trim();
    int pageSecurisee = Math.max(0, page);
    int tailleSecurisee = Math.max(1, Math.min(size, TAILLE_PAGE_MAX));

    Page<JournalAudit> resultats =
        repository.rechercher(
            module == null ? null : module.name(),
            utilisateurId,
            debutInstant,
            finInstant,
            termeNettoye,
            PageRequest.of(pageSecurisee, tailleSecurisee));
    return resultats.map(JournalAuditReponse::depuis);
  }
}
