package ma.hbdev.rh.shared.export;

/** Formats supportés par le module Export (EF-EXP-01/02/03, EF-CFG-05). */
public enum FormatExport {
  xlsx("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
  pdf("application/pdf", ".pdf");

  private final String typeMime;
  private final String extension;

  FormatExport(String typeMime, String extension) {
    this.typeMime = typeMime;
    this.extension = extension;
  }

  public String typeMime() {
    return typeMime;
  }

  public String extension() {
    return extension;
  }
}
