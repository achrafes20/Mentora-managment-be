package ma.hbdev.rh.administrative;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.export.FormatExport;
import ma.hbdev.rh.shared.file.FileStorageService;
import ma.hbdev.rh.shared.security.CurrentUser;
import ma.hbdev.rh.shared.web.ApiResponse;
import ma.hbdev.rh.shared.web.PagedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/demandes-administratives")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
class AdministrativeController {

  private final AdministrativeService service;
  private final JourFerieService jourFerieService;
  private final PeriodeBlocageCongesService periodeBlocageCongesService;
  private final PolitiqueCongeService politiqueCongeService;
  private final FileStorageService fileStorageService;

  AdministrativeController(
      AdministrativeService service,
      JourFerieService jourFerieService,
      PeriodeBlocageCongesService periodeBlocageCongesService,
      PolitiqueCongeService politiqueCongeService,
      FileStorageService fileStorageService) {
    this.service = service;
    this.jourFerieService = jourFerieService;
    this.periodeBlocageCongesService = periodeBlocageCongesService;
    this.politiqueCongeService = politiqueCongeService;
    this.fileStorageService = fileStorageService;
  }

  @GetMapping
  ApiResponse<PagedResponse<DemandeAdministrativeReponse>> lister(
      @RequestParam(required = false) UUID employeId,
      @RequestParam(required = false) TypeDemandeAdministrative type,
      @RequestParam(required = false) StatutDemandeAdministrative statut,
      @RequestParam(required = false) LocalDate debut,
      @RequestParam(required = false) LocalDate fin,
      Pageable pageable) {
    return ApiResponse.ok(
        PagedResponse.of(service.lister(employeId, type, statut, debut, fin, pageable)));
  }

  // EF-EXP-03 : réutilise lister() (mêmes filtres/périmètre) — chemin statique, aucune ambiguïté
  // avec "/{id}/..." (pas de mapping "/{id}" seul sur ce contrôleur).
  @GetMapping("/export")
  ResponseEntity<byte[]> exporter(
      @RequestParam FormatExport format,
      @RequestParam(required = false) UUID employeId,
      @RequestParam(required = false) TypeDemandeAdministrative type,
      @RequestParam(required = false) StatutDemandeAdministrative statut,
      @RequestParam(required = false) LocalDate debut,
      @RequestParam(required = false) LocalDate fin) {
    byte[] contenu = service.exporter(employeId, type, statut, debut, fin, format);
    String nomFichier = "demandes_administratives_" + LocalDate.now() + format.extension();
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(format.typeMime()))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(nomFichier).build().toString())
        .body(contenu);
  }

  @PostMapping
  ApiResponse<DemandeAdministrativeReponse> creer(
      @Valid @RequestBody DemandeAdministrativeRequete requete) {
    return ApiResponse.ok(service.creer(requete));
  }

  // EF-ADM-14 : justificatif (arrêt de travail...) téléversé avant la création de la demande —
  // même mécanisme que la photo/les documents employé (shared/file), l'UUID retourné est ensuite
  // passé en fichierDocumentLibreId dans DemandeAdministrativeRequete.
  @PostMapping("/justificatif")
  ApiResponse<UUID> televerserJustificatif(@RequestParam("fichier") MultipartFile fichier) {
    UUID televersePar = CurrentUser.id().orElse(null);
    return ApiResponse.ok(fileStorageService.televerser(fichier, televersePar).id());
  }

  // EF-AUTH-11/12 : décision + actions adjacentes ouvertes au délégué actif, même précédent que
  // CandidatureController/OffreEmploiController (T3.B1) — jours fériés/config restent Admin-only.
  @PatchMapping("/{id}/approuver")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  ApiResponse<DemandeAdministrativeReponse> approuver(@PathVariable UUID id) {
    return ApiResponse.ok(service.approuver(id));
  }

  @PatchMapping("/{id}/rejeter")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  ApiResponse<DemandeAdministrativeReponse> rejeter(@PathVariable UUID id) {
    return ApiResponse.ok(service.rejeter(id));
  }

  @PatchMapping("/{id}/annuler")
  @PreAuthorize("hasRole('ADMIN') or @delegationService.estDelegueActif()")
  ApiResponse<DemandeAdministrativeReponse> annuler(@PathVariable UUID id) {
    return ApiResponse.ok(service.annuler(id));
  }

  @GetMapping("/employes/{employeId}/solde")
  ApiResponse<SoldeCongeReponse> solde(@PathVariable UUID employeId) {
    return ApiResponse.ok(service.solde(employeId));
  }

  @GetMapping("/employes/{employeId}/mouvements")
  ApiResponse<List<MouvementCongeReponse>> mouvements(@PathVariable UUID employeId) {
    return ApiResponse.ok(service.mouvements(employeId));
  }

  @GetMapping("/jours-feries")
  ApiResponse<List<JourFerieReponse>> joursFeries() {
    return ApiResponse.ok(jourFerieService.lister());
  }

  @PostMapping("/jours-feries")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<JourFerieReponse> creerJourFerie(@Valid @RequestBody JourFerieRequete requete) {
    return ApiResponse.ok(jourFerieService.creer(requete));
  }

  @DeleteMapping("/jours-feries/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<Void> supprimerJourFerie(@PathVariable UUID id) {
    jourFerieService.supprimer(id);
    return ApiResponse.ok();
  }

  // EF-ADM-12 : gestion réservée Admin (comme les jours fériés), jamais délégable.
  @GetMapping("/periodes-blocage-conges")
  ApiResponse<List<PeriodeBlocageCongesReponse>> periodesBlocageConges() {
    return ApiResponse.ok(periodeBlocageCongesService.lister());
  }

  @PostMapping("/periodes-blocage-conges")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<PeriodeBlocageCongesReponse> creerPeriodeBlocageConges(
      @Valid @RequestBody PeriodeBlocageCongesRequete requete) {
    return ApiResponse.ok(periodeBlocageCongesService.creer(requete));
  }

  @DeleteMapping("/periodes-blocage-conges/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<Void> supprimerPeriodeBlocageConges(@PathVariable UUID id) {
    periodeBlocageCongesService.supprimer(id);
    return ApiResponse.ok();
  }

  // EF-ADM-11 : gestion réservée Admin, jamais délégable.
  @GetMapping("/politique-conges")
  ApiResponse<List<PolitiqueCongeReponse>> politiqueConges() {
    return ApiResponse.ok(politiqueCongeService.lister());
  }

  @PutMapping("/politique-conges/{typeContrat}")
  @PreAuthorize("hasRole('ADMIN')")
  ApiResponse<PolitiqueCongeReponse> modifierPolitiqueConge(
      @PathVariable String typeContrat, @Valid @RequestBody PolitiqueCongeRequete requete) {
    return ApiResponse.ok(politiqueCongeService.modifier(typeContrat, requete.joursParMois()));
  }
}
