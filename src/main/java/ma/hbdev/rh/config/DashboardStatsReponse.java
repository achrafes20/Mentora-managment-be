package ma.hbdev.rh.config;

import java.util.List;

/**
 * EF-DASH-01/02 : agrégats lecture seule du tableau de bord — renvoyés à chaque chargement de page
 * (EF-DASH-04). Aucune table dédiée : toutes les valeurs sont calculées sur l'existant.
 */
public record DashboardStatsReponse(

    // --- Admin + Manager (portées différentes) ---

    /** Nombre d'employés actifs dans le périmètre (total pour Admin, département pour Manager). */
    long employesActifs,

    /** Nombre de demandes administratives en statut {@code en_attente} dans le périmètre. */
    long demandesEnAttente,

    /**
     * Nombre d'anomalies de pointage non résolues du jour dans le périmètre. "Du jour" = créées
     * aujourd'hui (date_pointage = today).
     */
    long anomaliesDuJour,

    // --- Admin uniquement (null / absent pour Manager) ---

    /**
     * Répartition par département : id, nom, nombre d'employés actifs. Null pour un Manager
     * (utilise son département directement).
     */
    List<RepartitionDepartement> repartitionParDepartement,

    /**
     * Nombre de candidatures en pipeline actif (toutes étapes confondues, hors {@code embauche},
     * {@code rejete}, {@code archivee}, {@code non_traite}) — EF-DASH-01.
     */
    Long candidaturesEnCours,

    /**
     * Nombre de stagiaires (STAGIAIRE + STAGIAIRE_REMUNERE) dont la fin de contrat est dans les 7
     * jours — EF-DASH-01.
     */
    Long finContratDans7Jours) {

  /** Paire département → effectif actif, pour la carte « Employés actifs » Admin. */
  public record RepartitionDepartement(String departementId, String nom, long count) {}
}
