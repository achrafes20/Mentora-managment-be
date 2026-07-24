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
    UUID logoFichierId,
    UUID modifiePar,
    Instant modifieLe) {

  private static final IdentiteEntrepriseReponse VIDE =
      new IdentiteEntrepriseReponse(null, null, null, null, null, null, null, null);

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
        entite.getLogoFichierId(),
        entite.getModifiePar(),
        entite.getModifieLe());
  }
}
