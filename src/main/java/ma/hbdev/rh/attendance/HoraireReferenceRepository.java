package ma.hbdev.rh.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HoraireReferenceRepository extends JpaRepository<HoraireReference, UUID> {

  /** Retourne l'horaire en vigueur à la date donnée : le dernier dont date_effet <= date. */
  @Query(
      "SELECT h FROM HoraireReference h WHERE h.dateEffet <= :date ORDER BY h.dateEffet DESC LIMIT 1")
  Optional<HoraireReference> findEnVigueurA(@Param("date") LocalDate date);

  List<HoraireReference> findAllByOrderByDateEffetDesc();
}
