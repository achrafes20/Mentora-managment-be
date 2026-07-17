package ma.hbdev.rh.shared.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * Résultat structuré d'une analyse de CV (EF-REC-04) : (a) données personnelles extraites,
 * réutilisées pour le pré-remplissage employé (EF-EMP-05) ; (b) analyse de correspondance avec
 * l'offre visée. Tout champ non détectable par l'IA est {@code null}.
 */
public record AnalyseResultat(
    String prenom,
    String nom,
    String email,
    String telephone,
    String intitulePoste,
    BigDecimal scoreCorrespondance,
    BigDecimal anneesExperienceEstimees,
    String justificationScore,
    List<String> motsCles) {}
