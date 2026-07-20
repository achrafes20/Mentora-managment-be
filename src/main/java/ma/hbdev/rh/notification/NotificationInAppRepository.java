package ma.hbdev.rh.notification;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationInAppRepository extends JpaRepository<NotificationInApp, UUID> {

  Page<NotificationInApp> findByDestinataireIdAndArchiveeLeIsNull(
      UUID destinataireId, Pageable pageable);

  Optional<NotificationInApp> findByIdAndDestinataireIdAndArchiveeLeIsNull(
      UUID id, UUID destinataireId);

  long countByDestinataireIdAndLuFalseAndArchiveeLeIsNull(UUID destinataireId);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      update NotificationInApp n
         set n.lu = true, n.luLe = :maintenant
       where n.destinataireId = :destinataireId
         and n.lu = false
         and n.archiveeLe is null
      """)
  int marquerToutesLues(
      @Param("destinataireId") UUID destinataireId, @Param("maintenant") Instant maintenant);

  @Modifying(clearAutomatically = true)
  @Query(
      """
      update NotificationInApp n
         set n.archiveeLe = :maintenant
       where n.archiveeLe is null
         and n.creeLe < :dateLimite
      """)
  int archiverAvant(
      @Param("dateLimite") Instant dateLimite, @Param("maintenant") Instant maintenant);
}
