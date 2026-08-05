package ma.hbdev.rh.recruitment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class OffreEmploiService {

  private final OffreEmploiRepository offreEmploiRepository;
  private final CandidatureRepository candidatureRepository;
  private final ApplicationEventPublisher evenements;
  private final ObjectMapper objectMapper;

  // EF-REC-12 : constantes techniques fixées au déploiement (cf. décision T4.B2 du 2026-07-24).
  private final int seuilReactivation;
  private final int fenetreRetentionMois;

  OffreEmploiService(
      OffreEmploiRepository offreEmploiRepository,
      CandidatureRepository candidatureRepository,
      ApplicationEventPublisher evenements,
      ObjectMapper objectMapper,
      @Value("${app.recruitment.reactivation.seuil-score:70}") int seuilReactivation,
      @Value("${app.recruitment.reactivation.fenetre-mois:6}") int fenetreRetentionMois) {
    this.offreEmploiRepository = offreEmploiRepository;
    this.candidatureRepository = candidatureRepository;
    this.evenements = evenements;
    this.objectMapper = objectMapper;
    this.seuilReactivation = seuilReactivation;
    this.fenetreRetentionMois = fenetreRetentionMois;
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
    evenements.publishEvent(
        new OffreEmploiModifieEvent(offre.getId(), "creation", offre.getIntitule()));
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
    evenements.publishEvent(
        new OffreEmploiModifieEvent(offre.getId(), "modification", offre.getIntitule()));
    return offre;
  }

  OffreEmploi fermer(UUID id) {
    OffreEmploi offre = trouver(id);
    offre.fermer();
    evenements.publishEvent(
        new OffreEmploiModifieEvent(offre.getId(), "fermeture", offre.getIntitule()));
    return offre;
  }

  OffreEmploi rouvrir(UUID id) {
    OffreEmploi offre = trouver(id);
    offre.rouvrir();
    evenements.publishEvent(
        new OffreEmploiModifieEvent(offre.getId(), "reouverture", offre.getIntitule()));
    return offre;
  }

  private void reactiverCandidaturesCompatibles(OffreEmploi offre) {
    List<String> motsClesOffre = MotsClesUtils.versListe(offre.getMotsClesRequis());
    if (motsClesOffre.isEmpty()) {
      return;
    }
    Instant seuilRetention = Instant.now().minus(fenetreRetentionMois * 30L, ChronoUnit.DAYS);

    List<Candidature> enAttente =
        candidatureRepository.findByStatutAndDateIngestionAfter(
            StatutCandidature.en_attente, seuilRetention);
    for (Candidature candidature : enAttente) {
      List<String> motsClesCandidat =
          MotsClesUtils.versListe(motsClesDeLaDerniereAnalyse(candidature));
      if (motsClesCandidat.isEmpty()) {
        continue;
      }
      if (chevauchementPourcent(motsClesOffre, motsClesCandidat) >= seuilReactivation) {
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
