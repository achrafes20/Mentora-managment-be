package ma.hbdev.rh.recruitment;

import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** EF-REC-09 : le Manager assigné saisit le résultat de l'entretien. */
@Service
@Transactional
class EntretienService {

  private final EntretienRepository entretienRepository;
  private final CandidatureService candidatureService;

  EntretienService(EntretienRepository entretienRepository, CandidatureService candidatureService) {
    this.entretienRepository = entretienRepository;
    this.candidatureService = candidatureService;
  }

  @Transactional(readOnly = true)
  List<Entretien> historique(UUID candidatureId) {
    return entretienRepository.findByCandidatureIdOrderByCreeLeDesc(candidatureId);
  }

  Entretien enregistrerResultat(
      UUID candidatureId, ResultatEntretien resultat, String commentaire) {
    Entretien entretien =
        entretienRepository.findByCandidatureIdOrderByCreeLeDesc(candidatureId).stream()
            .findFirst()
            .orElseThrow(() -> new EntretienIntrouvableException(candidatureId));
    verifierManagerAssigne(entretien);
    entretien.enregistrerResultat(resultat, commentaire);
    // EF-REC-07 : seule voie vers "decision" depuis "entretien" — jamais un bouton Admin manuel
    // (décision confirmée avec Taha).
    candidatureService.avancerVersDecisionDepuisEntretien(candidatureId);
    return entretien;
  }

  // Manager-only désormais (RBAC contrôleur) : le manager assigné doit correspondre exactement,
  // plus de contournement Admin — c'est justement ce qui a été retiré (l'Admin ne saisit plus de
  // résultat, cf. plan T3.B1 révisé).
  private void verifierManagerAssigne(Entretien entretien) {
    UUID courant = CurrentUser.id().orElse(null);
    if (courant == null || !courant.equals(entretien.getManagerId())) {
      throw new EntretienManagerNonAssigneException();
    }
  }
}
