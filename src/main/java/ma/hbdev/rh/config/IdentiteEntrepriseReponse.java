package ma.hbdev.rh.config;

import java.time.Instant;
import java.util.UUID;

/**
 * EF-CFG-01 : publique — consommée aussi par de futurs modules (ex. {@code document}, T4.A1, pour
 * générer les certificats) via {@link IdentiteEntrepriseService#obtenir()}.
 */
public record IdentiteEntrepriseReponse(
    UUID id,
    String raisonSociale,
    String adresse,
    String telephone,
    String email,
    String ice,
    String rc,
    String ville,
    UUID logoFichierId,
    UUID signatureFichierId,
    String signataireNom,
    String signataireFonction,
    String signataireSexe,
    UUID modifiePar,
    Instant modifieLe) {

  private static final IdentiteEntrepriseReponse VIDE =
      new IdentiteEntrepriseReponse(
          null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

  static IdentiteEntrepriseReponse vide() {
    return VIDE;
  }

  static IdentiteEntrepriseReponse depuis(IdentiteEntreprise entite) {
    return new IdentiteEntrepriseReponse(
        entite.getId(),
        entite.getRaisonSociale(),
        entite.getAdresse(),
        entite.getTelephone(),
        entite.getEmail(),
        entite.getIce(),
        entite.getRc(),
        entite.getVille(),
        entite.getLogoFichierId(),
        entite.getSignatureFichierId(),
        entite.getSignataireNom(),
        entite.getSignataireFonction(),
        entite.getSignataireSexe() == null ? null : entite.getSignataireSexe().name(),
        entite.getModifiePar(),
        entite.getModifieLe());
  }
}
