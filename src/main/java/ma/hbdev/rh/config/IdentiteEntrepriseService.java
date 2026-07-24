package ma.hbdev.rh.config;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ma.hbdev.rh.shared.file.FileStorageService;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * EF-CFG-01/02 : identité de l'entreprise — table à une seule ligne logique (pas de contrainte SQL,
 * garanti côté service par "prendre la première ligne existante, sinon en créer une"). Service
 * public : futur point d'entrée pour {@code document} (T4.A1, génération de certificats).
 */
@Service
@Transactional
public class IdentiteEntrepriseService {

  private final IdentiteEntrepriseRepository repository;
  private final FileStorageService fileStorageService;
  private final ApplicationEventPublisher evenements;

  IdentiteEntrepriseService(
      IdentiteEntrepriseRepository repository,
      FileStorageService fileStorageService,
      ApplicationEventPublisher evenements) {
    this.repository = repository;
    this.fileStorageService = fileStorageService;
    this.evenements = evenements;
  }

  @Transactional(readOnly = true)
  public IdentiteEntrepriseReponse obtenir() {
    return ligneUnique()
        .map(IdentiteEntrepriseReponse::depuis)
        .orElseGet(IdentiteEntrepriseReponse::vide);
  }

  IdentiteEntrepriseReponse modifier(IdentiteEntrepriseRequete requete) {
    IdentiteEntreprise entite = ligneUnique().orElseGet(IdentiteEntreprise::new);
    entite.setRaisonSociale(requete.raisonSociale());
    entite.setAdresse(requete.adresse());
    entite.setTelephone(requete.telephone());
    entite.setEmail(requete.email());
    entite.setModifiePar(CurrentUser.id().orElse(null));
    entite.setModifieLe(Instant.now());
    // Le save doit précéder la publication de l'événement : sur la toute première modification,
    // l'entité est nouvelle et son id (GenerationType.UUID) n'existe qu'après persistance.
    IdentiteEntreprise sauvegardee = repository.save(entite);
    evenements.publishEvent(new IdentiteEntrepriseModifieEvent(sauvegardee.getId()));
    return IdentiteEntrepriseReponse.depuis(sauvegardee);
  }

  IdentiteEntrepriseReponse televerserLogo(MultipartFile fichier, UUID televersePar) {
    IdentiteEntreprise entite = ligneUnique().orElseGet(IdentiteEntreprise::new);
    var uploade = fileStorageService.televerser(fichier, televersePar);
    entite.setLogoFichierId(uploade.id());
    entite.setModifiePar(televersePar);
    entite.setModifieLe(Instant.now());
    IdentiteEntreprise sauvegardee = repository.save(entite);
    evenements.publishEvent(new IdentiteEntrepriseModifieEvent(sauvegardee.getId()));
    return IdentiteEntrepriseReponse.depuis(sauvegardee);
  }

  @Transactional(readOnly = true)
  LogoEntrepriseTelecharge recupererLogo() {
    UUID logoFichierId = ligneUnique().map(IdentiteEntreprise::getLogoFichierId).orElse(null);
    if (logoFichierId == null) {
      throw new LogoEntrepriseIntrouvableException();
    }
    var metadonnees = fileStorageService.recuperer(logoFichierId);
    var ressource = fileStorageService.charger(logoFichierId);
    return new LogoEntrepriseTelecharge(ressource, metadonnees.typeMime());
  }

  private Optional<IdentiteEntreprise> ligneUnique() {
    return repository.findAll().stream().findFirst();
  }
}
