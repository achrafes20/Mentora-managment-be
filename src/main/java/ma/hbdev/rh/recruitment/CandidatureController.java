package ma.hbdev.rh.recruitment;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** EF-REC-06/07/09/10/11/12/14. Consultation Admin/Manager (scopée pour le Manager, EF-REC-08). */
@RestController
@RequestMapping("/api/candidatures")
public class CandidatureController {

  private final CandidatureService candidatureService;
  private final CandidatureIngestionService candidatureIngestionService;
  private final EntretienService entretienService;

  public CandidatureController(
      CandidatureService candidatureService,
      CandidatureIngestionService candidatureIngestionService,
      EntretienService entretienService) {
    this.candidatureService = candidatureService;
    this.candidatureIngestionService = candidatureIngestionService;
    this.entretienService = entretienService;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<PagedResponse<CandidatureReponse>> lister(
      @RequestParam(required = false) UUID offreId,
      @RequestParam(required = false) StatutCandidature statut,
      @RequestParam(required = false) BigDecimal scoreMin,
      @RequestParam(required = false) String recherche,
      Pageable pageable) {
    var page =
        candidatureService
            .lister(offreId, statut, scoreMin, recherche, pageable)
            .map(CandidatureReponse::depuis);
    return ApiResponse.ok(PagedResponse.of(page));
  }

  // Candidat reçu au bureau (EF-REC, hors ingestion e-mail) — même pipeline dédup/stockage CV que
  // l'ingestion, offre optionnelle (cf. CandidatureIngestionService#creerManuellement pour le
  // raisonnement complet), aucune analyse IA automatique.
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  @ResponseStatus(HttpStatus.CREATED)
  public ApiResponse<CandidatureReponse> creerManuellement(
      @RequestParam String nom,
      @RequestParam String prenom,
      @RequestParam String email,
      @RequestParam(required = false) String telephone,
      @RequestParam(required = false) UUID offreId,
      @RequestParam(required = false) String notes,
      @RequestParam(required = false) MultipartFile cv) {
    Candidature candidature =
        candidatureIngestionService.creerManuellement(
            offreId, nom, prenom, email, telephone, cv, notes);
    return ApiResponse.ok(CandidatureReponse.depuis(candidature));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<CandidatureReponse> detail(@PathVariable UUID id) {
    return ApiResponse.ok(CandidatureReponse.depuis(candidatureService.trouver(id)));
  }

  @PostMapping("/{id}/statut")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  public ApiResponse<CandidatureReponse> changerStatut(
      @PathVariable UUID id, @Valid @RequestBody ChangerStatutRequete requete) {
    return ApiResponse.ok(
        CandidatureReponse.depuis(
            candidatureService.changerStatut(
                id,
                requete.statut(),
                requete.managerId(),
                requete.dateEntretien(),
                requete.corpsMessage())));
  }

  // EF-REC-09 : seule action de l'Admin pendant l'étape Entretien (avec le rejet) — tant qu'aucun
  // résultat n'a été rendu par le Manager.
  @PostMapping("/{id}/entretien/reprogrammer")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  public ApiResponse<EntretienReponse> reprogrammerEntretien(
      @PathVariable UUID id, @Valid @RequestBody ReprogrammerEntretienRequete requete) {
    return ApiResponse.ok(
        EntretienReponse.depuis(
            candidatureService.reprogrammerEntretien(
                id, requete.managerId(), requete.dateEntretien())));
  }

  @PostMapping("/{id}/relancer-analyse")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  public ApiResponse<CandidatureReponse> relancerAnalyse(@PathVariable UUID id) {
    candidatureIngestionService.relancerAnalyse(id);
    return ApiResponse.ok(CandidatureReponse.depuis(candidatureService.trouver(id)));
  }

  @PostMapping("/{id}/reactiver")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  public ApiResponse<CandidatureReponse> validerReactivation(@PathVariable UUID id) {
    return ApiResponse.ok(CandidatureReponse.depuis(candidatureService.validerReactivation(id)));
  }

  // Déclenchement manuel de l'archivage par un Admin authentifié (bouton "Forcer exécution" côté
  // écran Candidatures) — même principe que DocumentRhController#executerSurveillance. L'exécution
  // planifiée (tous les jours à 3h, Africa/Casablanca) reste CandidatureService#archivageQuotidien
  // ;
  // ceci ne fait que la rejouer à la demande, RBAC normal (pas de secret partagé, contrairement à
  // l'ancien ping n8n — EF-REC-12, cf. ai-instructions.md règle 7 révisée).
  @PostMapping("/archiver-expirees")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Integer> archiverExpirees() {
    return ApiResponse.ok(candidatureService.archiverExpirees());
  }

  // "inline" (pas "attachment") : même convention que EmployeController.telechargerDocument —
  // laisse le navigateur prévisualiser le PDF/DOCX quand il le peut.
  @GetMapping("/{id}/cv")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ResponseEntity<Resource> telechargerCv(@PathVariable UUID id) {
    CandidatureCvTelecharge telecharge = candidatureService.telechargerCv(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(telecharge.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.inline().filename(telecharge.nomOriginal()).build().toString())
        .body(telecharge.ressource());
  }

  @GetMapping("/{id}/entretiens")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  public ApiResponse<List<EntretienReponse>> historiqueEntretiens(@PathVariable UUID id) {
    return ApiResponse.ok(
        entretienService.historique(id).stream().map(EntretienReponse::depuis).toList());
  }

  // Manager uniquement — l'Admin ne saisit jamais de résultat d'entretien (décision confirmée
  // avec Taha), il ne peut que rejeter ou reprogrammer (cf. reprogrammerEntretien ci-dessus).
  @PostMapping("/{id}/entretien")
  @PreAuthorize("hasRole('MANAGER')")
  public ApiResponse<EntretienReponse> enregistrerResultatEntretien(
      @PathVariable UUID id, @Valid @RequestBody ResultatEntretienRequete requete) {
    return ApiResponse.ok(
        EntretienReponse.depuis(
            entretienService.enregistrerResultat(id, requete.resultat(), requete.commentaire())));
  }

  @ExceptionHandler(CandidatureIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererIntrouvable(CandidatureIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(OffreEmploiIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererOffreIntrouvable(OffreEmploiIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(CandidatureDejaExistanteException.class)
  ResponseEntity<ApiResponse<Void>> gererDejaExistante(CandidatureDejaExistanteException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(TransitionCandidatureInvalideException.class)
  ResponseEntity<ApiResponse<Void>> gererTransitionInvalide(
      TransitionCandidatureInvalideException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(EntretienIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererEntretienIntrouvable(EntretienIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(EntretienManagerNonAssigneException.class)
  ResponseEntity<ApiResponse<Void>> gererManagerNonAssigne(EntretienManagerNonAssigneException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(CandidatureCvIntrouvableException.class)
  ResponseEntity<ApiResponse<Void>> gererCvIntrouvable(CandidatureCvIntrouvableException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(EntretienDejaResoluException.class)
  ResponseEntity<ApiResponse<Void>> gererEntretienDejaResolu(EntretienDejaResoluException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(ManagerRequisPourEntretienException.class)
  ResponseEntity<ApiResponse<Void>> gererManagerRequis(ManagerRequisPourEntretienException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }
}
