package ma.hbdev.rh.document;

/**
 * Corps optionnel de la requête d'envoi de certificat de stage (EF-DOC-04). Le sujet de stage n'est
 * pas persisté sur la fiche employé (aucun champ dédié aujourd'hui) — il est saisi ponctuellement
 * par l'Admin RH à chaque génération, et n'apparaît que dans le PDF produit.
 */
record CertificatStageRequete(String sujetStage) {}
