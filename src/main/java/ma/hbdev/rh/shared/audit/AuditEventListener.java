package ma.hbdev.rh.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import ma.hbdev.rh.auth.DelegationService;
import ma.hbdev.rh.shared.event.EvenementMetier;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** NFR-SEC-03 : chaque evenement metier est ajoute au journal append-only. */
@Component
@RequiredArgsConstructor
class AuditEventListener {

  private final JournalAuditRepository repository;
  private final ObjectMapper objectMapper;
  private final DelegationService delegationService;

  @EventListener
  @Order(30)
  void journaliser(EvenementMetier evenement) {
    if (!evenement.audite()) {
      return;
    }
    JsonNode details = objectMapper.valueToTree(evenement.details());
    UUID delegationId =
        evenement.decisionDelegable() ? delegationService.delegationActiveId().orElse(null) : null;
    repository.save(
        new JournalAudit(evenement, CurrentUser.id().orElse(null), details, delegationId));
  }
}
