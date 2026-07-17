package ma.hbdev.rh.recruitment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CandidatureRepository
    extends JpaRepository<Candidature, UUID>, JpaSpecificationExecutor<Candidature> {

  // analyseCourante est LAZY et optionnelle (LEFT JOIN, pas de candidature sans analyse exclue) :
  // fetch-join explicite pour tout chemin de lecture qui mappe vers un DTO en dehors de la
  // transaction (sinon LazyInitializationException côté contrôleur — même principe que
  // EmployeRepository.findByIdAvecDepartement).
  @Query("SELECT c FROM Candidature c LEFT JOIN FETCH c.analyseCourante WHERE c.id = :id")
  Optional<Candidature> findByIdAvecAnalyseCourante(@Param("id") UUID id);

  // EF-REC-11/EF-REC-12 : bloc "en_attente" reçu tant qu'aucune offre compatible n'est ouverte.
  Optional<Candidature> findByOffreIdAndEmail(UUID offreId, String email);

  // EF-REC-12 : candidatures "en_attente" encore dans la fenêtre de rétention -> éligibles à une
  // suggestion de réactivation à la création d'une nouvelle offre.
  List<Candidature> findByStatutAndDateIngestionAfter(StatutCandidature statut, Instant seuil);

  // EF-REC-12 (tail) : candidatures "en_attente" au-delà de la fenêtre de rétention -> archivage
  // automatique (pas de suppression physique, NFR-DATA-01).
  List<Candidature> findByStatutAndDateIngestionBefore(StatutCandidature statut, Instant seuil);
}
