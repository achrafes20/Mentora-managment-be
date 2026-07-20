package ma.hbdev.rh.auth;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    RoleUtilisateur role,
    String nom,
    String prenom,
    String mattermostUserId,
    StatutActifInactif statut,
    Instant creeLe,
    Instant modifieLe) {
  public static UserResponse fromUser(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getRole(),
        user.getNom(),
        user.getPrenom(),
        user.getMattermostUserId(),
        user.getStatut(),
        user.getCreeLe(),
        user.getModifieLe());
  }
}
