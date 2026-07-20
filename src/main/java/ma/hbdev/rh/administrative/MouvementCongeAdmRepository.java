package ma.hbdev.rh.administrative;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MouvementCongeAdmRepository extends JpaRepository<MouvementCongeAdm, UUID> {

  List<MouvementCongeAdm> findByEmployeIdOrderByDateMouvementDescCreeLeDesc(UUID employeId);

  boolean existsByDemandeIdAndTypeMouvement(UUID demandeId, TypeMouvementCongeAdm typeMouvement);

  @Query(
      "select coalesce(sum(m.quantiteJours), 0) from MouvementCongeAdm m where m.employeId = :employeId")
  BigDecimal sommeMouvements(UUID employeId);
}
