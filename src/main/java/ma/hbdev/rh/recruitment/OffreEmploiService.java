package ma.hbdev.rh.recruitment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import ma.hbdev.rh.shared.config.ConfigurationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class OffreEmploiService {

  private final OffreEmploiRepository offreEmploiRepository;
  private final CandidatureRepository candidatureRepository;
  private final ConfigurationService configurationService;
  private final ApplicationEventPublisher evenements;
  private final ObjectMapper objectMapper;

  OffreEmploiService(
      OffreEmploiRepository offreEmploiRepository,
      CandidatureRepository candidatureRepository,
      ConfigurationService configurationService,
      ApplicationEventPublisher evenements,
      ObjectMapper objectMapper) {
    this.offreEmploiRepository = offreEmploiRepository;
    this.candidatureRepository = candidatureRepository;
    this.configurationService = configurationService;
    this.evenements = evenements;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  List<OffreEmploi> lister(StatutOffreEmploi statut) {
    return statut != null
        ? offreEmploiRepository.findByStatut(statut)
        : offreEmploiRepository.findAll();
  }

  @Transactional(readOnly = true)
  OffreEmploi trouver(UUID id) {
    return offreEmploiRepository
        .findById(id)
        .orElseThrow(() -> new OffreEmploiIntrouvableException(id));
  }

  OffreEmploi creer(OffreEmploiRequete requete, UUID creePar) {
    OffreEmploi offre =
        offreEmploiRepository.save(
            new OffreEmploi(
                requete.intitule(),
                requete.description(),
                requete.departementId(),
                MotsClesUtils.versJsonNode(requete.motsClesRequis(), objectMapper),
                creePar));
    evenements.publishEvent(new OffreEmploiModifieEvent(offre.getId(), "creation"));
    // EF-REC-11/12 : à la création, on réévalue les candidatures "en_attente" contre les
    // nouveaux mots-clés requis.
    reactiverCandidaturesCompatibles(offre);
    return offre;
  }

  OffreEmploi modifier(UUID id, OffreEmploiRequete requete) {
    OffreEmploi offre = trouver(id);
    offre.modifier(
        requete.intitule(),
        requete.description(),
        requete.departementId(),
        MotsClesUtils.versJsonNode(requete.motsClesRequis(), objectMapper));
    evenements.publishEvent(new OffreEmploiModifieEvent(offre.getId(), "modification"));
    return offre;
  }

  OffreEmploi fermer(UUID id) {
    OffreEmploi offre = trouver(id);
    offre.fermer();
    evenements.publishEvent(new OffreEmploiModifieEvent(offre.getId(), "fermeture"));
    return offre;
  }

  OffreEmploi rouvrir(UUID id) {
    OffreEmploi offre = trouver(id);
    offre.rouvrir();
    evenements.publishEvent(new OffreEmploiModifieEvent(offre.getId(), "reouverture"));
    return offre;
  }

  private void reactiverCandidaturesCompatibles(OffreEmploi offre) {
    List<String> motsClesOffre = MotsClesUtils.versListe(offre.getMotsClesRequis());
    if (motsClesOffre.isEmpty()) {
      return;
    }
    int seuil = configurationService.getInteger("seuil_score_reactivation_candidature", 70);
    int fenetreMois = configurationService.getInteger("fenetre_retention_candidature_mois", 6);
    Instant seuilRetention = Instant.now().minus(fenetreMois * 30L, ChronoUnit.DAYS);

    List<Candidature> enAttente =
        candidatureRepository.findByStatutAndDateIngestionAfter(
            StatutCandidature.en_attente, seuilRetention);
    for (Candidature candidature : enAttente) {
      List<String> motsClesCandidat =
          MotsClesUtils.versListe(motsClesDeLaDerniereAnalyse(candidature));
      if (motsClesCandidat.isEmpty()) {
        continue;
      }
      if (chevauchementPourcent(motsClesOffre, motsClesCandidat) >= seuil) {
        candidature.changerStatut(StatutCandidature.suggestion_reactivation);
        candidature.assignerOffre(offre.getId());
      }
    }
  }

  private static com.fasterxml.jackson.databind.JsonNode motsClesDeLaDerniereAnalyse(
      Candidature candidature) {
    AnalyseIa analyse = candidature.getAnalyseCourante();
    return analyse != null ? analyse.getMotsCles() : null;
  }

  private static int chevauchementPourcent(List<String> requis, List<String> candidat) {
    Set<String> requisNorm = requis.stream().map(String::toLowerCase).collect(Collectors.toSet());
    Set<String> candidatNorm =
        candidat.stream().map(String::toLowerCase).collect(Collectors.toSet());
    if (requisNorm.isEmpty()) {
      return 0;
    }
    long communs = requisNorm.stream().filter(candidatNorm::contains).count();
    return (int) Math.round(100.0 * communs / requisNorm.size());
  }
}
