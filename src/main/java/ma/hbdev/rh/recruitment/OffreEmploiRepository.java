package ma.hbdev.rh.recruitment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface OffreEmploiRepository extends JpaRepository<OffreEmploi, UUID> {

  List<OffreEmploi> findByStatut(StatutOffreEmploi statut);
}
