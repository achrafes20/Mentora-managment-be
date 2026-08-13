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

  // Serialise les appels concurrents de generer() pour un meme employe (verrou tenu jusqu'a la fin
  // de la transaction) : sans ca, deux revoquerTousActifsDe()+save() entrelaces peuvent chacun ne
  // voir aucun actif et laisser deux QR actifs simultanement (cf.
  // idx_qr_codes_employe_actif_unique).
  @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:employeId))", nativeQuery = true)
  void verrouillerPourEmploye(@Param("employeId") String employeId);
}
