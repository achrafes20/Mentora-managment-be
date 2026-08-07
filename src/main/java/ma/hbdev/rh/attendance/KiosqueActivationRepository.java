package ma.hbdev.rh.attendance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface KiosqueActivationRepository extends JpaRepository<KiosqueActivation, UUID> {

  Optional<KiosqueActivation> findByDeviceTokenHashAndStatut(
      String deviceTokenHash, StatutActivationKiosque statut);

  List<KiosqueActivation> findByStatutOrderByEmisLeDesc(StatutActivationKiosque statut);

  List<KiosqueActivation> findByStatutIn(List<StatutActivationKiosque> statuts);

  List<KiosqueActivation> findAllByOrderByEmisLeDesc();

  Optional<KiosqueActivation> findByJetonRevocationHash(String jetonRevocationHash);
}
