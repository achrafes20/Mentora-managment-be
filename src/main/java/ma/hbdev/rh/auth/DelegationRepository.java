package ma.hbdev.rh.auth;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface DelegationRepository extends JpaRepository<DelegationApprobation, UUID> {
  List<DelegationApprobation> findAllByOrderByCreeLeDesc();

  Optional<DelegationApprobation> findFirstByDelegueIdAndStatut(
      UUID delegueId, StatutDelegation statut);

  /** EF-AUTH-13/15 : délégations actives dont l'échéance est dépassée, jamais encore purgées. */
  List<DelegationApprobation> findByStatutAndDateFinBefore(StatutDelegation statut, LocalDate date);
}
