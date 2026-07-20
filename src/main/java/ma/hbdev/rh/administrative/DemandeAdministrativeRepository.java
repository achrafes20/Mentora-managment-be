package ma.hbdev.rh.administrative;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface DemandeAdministrativeRepository
    extends JpaRepository<DemandeAdministrative, UUID>,
        JpaSpecificationExecutor<DemandeAdministrative> {

  List<DemandeAdministrative> findByEmployeIdAndTypeDemandeAndStatut(
      UUID employeId, TypeDemandeAdministrative typeDemande, StatutDemandeAdministrative statut);

  boolean
      existsByEmployeIdAndTypeDemandeAndStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(
          UUID employeId,
          TypeDemandeAdministrative typeDemande,
          StatutDemandeAdministrative statut,
          LocalDate dateFin,
          LocalDate dateDebut);
}
