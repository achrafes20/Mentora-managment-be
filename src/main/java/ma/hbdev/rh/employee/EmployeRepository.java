package ma.hbdev.rh.employee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// EF-EMP-04 / EF-EMP-12 (filtres + recherche texte libre) passent par JpaSpecificationExecutor,
// pas un @Query JPQL : le motif "(:param IS NULL OR champ = :param)" laisse Postgres incapable
// de déterminer le type d'un paramètre lié à un enum natif (type_contrat_employe / statut) —
// "could not determine data type of parameter" côté driver JDBC. Specification force Hibernate à
// binder chaque paramètre avec son type exact tiré des métadonnées de l'entité.
interface EmployeRepository
    extends JpaRepository<Employe, UUID>, JpaSpecificationExecutor<Employe> {

  boolean existsByEmailIgnoreCase(String email);

  // EF-EMP-07 : clé de dédoublonnage de l'import — pas de matricule dans le modèle actuel
  // (aucune fiche employé n'en porte un, cf. 01-requirements.md EF-EMP-01), l'e-mail est le seul
  // champ métier réellement unique sur employes. Décision actée 2026-07-15 : import v1 sur
  // e-mail, à revoir si RH fournit un identifiant réel dans son fichier.
  Optional<Employe> findByEmailIgnoreCase(String email);

  boolean existsByDepartementIdAndStatut(UUID departementId, StatutActifInactif statut);

  List<Employe> findByDepartementIdAndStatut(UUID departementId, StatutActifInactif statut);

  // Departement est LAZY : fetch-join explicite pour tout chemin de lecture qui mappe vers un DTO
  // en dehors de la transaction (sinon LazyInitializationException côté contrôleur).
  @Query("SELECT e FROM Employe e JOIN FETCH e.departement WHERE e.id = :id")
  Optional<Employe> findByIdAvecDepartement(@Param("id") UUID id);
}
