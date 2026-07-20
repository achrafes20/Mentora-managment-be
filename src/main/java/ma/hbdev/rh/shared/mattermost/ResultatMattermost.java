package ma.hbdev.rh.shared.mattermost;

public record ResultatMattermost(boolean reussi, String erreur) {
  public static ResultatMattermost succes() {
    return new ResultatMattermost(true, null);
  }

  public static ResultatMattermost echec(String erreur) {
    return new ResultatMattermost(false, erreur);
  }
}
