package ma.hbdev.rh.administrative;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PeriodeBlocageCongesRepository extends JpaRepository<PeriodeBlocageConges, UUID> {

  List<PeriodeBlocageConges> findAllByOrderByDateDebutDesc();

  /** EF-ADM-12 : vrai si [debut, fin] chevauche au moins une période de blocage existante. */
  @Query(
      "select case when count(p) > 0 then true else false end from PeriodeBlocageConges p "
          + "where :debut <= p.dateFin and :fin >= p.dateDebut")
  boolean chevaucheUnePeriodeBloquee(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);
}
