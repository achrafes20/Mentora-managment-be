package ma.hbdev.rh.attendance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SiteQrCodeRepository extends JpaRepository<SiteQrCode, UUID> {

  Optional<SiteQrCode> findByValeurAndActifTrue(String valeur);

  List<SiteQrCode> findAllByOrderByCreeLeDesc();
}
