package ma.hbdev.rh.administrative;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface JourFerieRepository extends JpaRepository<JourFerie, java.util.UUID> {
  List<JourFerie> findByDateFerieBetweenOrderByDateFerie(LocalDate debut, LocalDate fin);

  List<JourFerie> findAllByOrderByDateFerieDesc();

  Optional<JourFerie> findByDateFerie(LocalDate dateFerie);
}
