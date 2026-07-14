package ma.hbdev.rh.attendance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface QrCodeRepository extends JpaRepository<QrCode, UUID> {

  Optional<QrCode> findByValeurAndActifTrue(String valeur);

  Optional<QrCode> findByEmployeIdAndActifTrue(UUID employeId);

  List<QrCode> findAllByEmployeId(UUID employeId);

  @Modifying
  @Query("UPDATE QrCode q SET q.actif = false WHERE q.employeId = :employeId AND q.actif = true")
  void revoquerTousActifsDe(@Param("employeId") UUID employeId);
}
