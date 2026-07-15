package ma.hbdev.rh.employee;

public record ImportChampSpecReponse(String cle, String libelle, boolean requis, String type) {

  static ImportChampSpecReponse depuis(ImportChampSpec spec) {
    return new ImportChampSpecReponse(
        spec.cle(), spec.libelle(), spec.requis(), spec.type().name());
  }
}
