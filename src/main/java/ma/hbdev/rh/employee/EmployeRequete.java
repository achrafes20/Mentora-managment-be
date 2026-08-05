package ma.hbdev.rh.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * EF-EMP-01 — création. Le département/manager initial se fixe ici ; tout changement ultérieur
 * passe par le transfert (EF-EMP-11).
 */
public record EmployeRequete(
    @NotBlank @Size(max = 100) String nom,
    @NotBlank @Size(max = 100) String prenom,
    @Email String email,
    String telephone,
    String poste,
    @NotNull UUID departementId,
    UUID managerId,
    @NotNull LocalDate dateEmbauche,
    @NotNull TypeContratEmploye typeContrat,
    LocalDate dateFinContratPrevue,
    // EF-DOC-12 : symétrique de dateFinContratPrevue mais réservé aux STAGIAIRE/STAGIAIRE_REMUNERE
    // — champ dédié car dateFinContratPrevue est contraint aux CDD (CHECK en base + validation).
    LocalDate dateFinStagePrevue,
    // EF-EMP-05/EF-REC-13 : renseigné par le frontend quand le formulaire "Nouvel employé" a été
    // ouvert depuis une candidature "Embauchée" (?depuisCandidatureId=), sinon null.
    UUID candidatureOrigineId,
    // Idem : le CV déjà stocké (shared/file) au moment de l'ingestion recrutement — attaché tel
    // quel comme document employé (EF-EMP-03) à la création, pas de reupload. Le frontend le lit
    // depuis CandidatureReponse.cvFichierId ; le module employé ne connaît jamais la table
    // `candidatures`, seulement l'UUID d'un fichier déjà stocké (ai-instructions.md règle 4).
    UUID cvFichierId,
    SexeEmploye sexe,
    String cin,
    // Optionnel, sans objet hors STAGIAIRE/STAGIAIRE_REMUNERE (cf. Employe#sujetStage).
    String sujetStage) {}
