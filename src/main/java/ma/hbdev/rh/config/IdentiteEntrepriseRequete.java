package ma.hbdev.rh.config;

record IdentiteEntrepriseRequete(
    String raisonSociale,
    String adresse,
    String telephone,
    String email,
    // Identifiants légaux marocains, affichés en en-tête des documents générés (EF-DOC).
    String ice,
    String rc,
    String ville,
    // Signataire des certificats générés (document.CertificatGenerator) — optionnel, dégrade vers
    // "La Direction des Ressources Humaines" / accord neutre si absent.
    String signataireNom,
    String signataireFonction,
    SexeSignataire signataireSexe) {}
