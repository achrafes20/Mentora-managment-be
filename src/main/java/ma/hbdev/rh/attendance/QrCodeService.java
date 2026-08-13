package ma.hbdev.rh.attendance;

import java.util.Optional;
import java.util.UUID;
import ma.hbdev.rh.employee.EmployeModifieEvent;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestion des QR codes des employés (EF-ATT-01). */
@Service
@Transactional
public class QrCodeService {

  private final QrCodeRepository repository;

  QrCodeService(QrCodeRepository repository) {
    this.repository = repository;
  }

  // EF-ATT-01 : un QR doit exister dès la création de la fiche, pas seulement au premier clic sur
  // "Régénérer la carte" (Admin uniquement). Avant ce correctif, un Manager qui consultait une
  // fiche neuve avant tout Admin restait bloqué avec une carte sans QR — genererQrCode() côté
  // frontend (fallback à l'affichage) n'est accessible qu'à l'Admin, donc rien ne se produisait
  // pour lui, sans erreur visible. Même pattern que KiosqueActivationService pour le même
  // événement.
  @EventListener
  void gererEvenementEmploye(EmployeModifieEvent event) {
    if ("creation".equals(event.action())) {
      generer(event.employeId());
    }
  }

  /** Génère un nouveau QR code pour un employé (révoque le précédent s'il existe). */
  public QrCode generer(UUID employeId) {
    repository.verrouillerPourEmploye(employeId.toString());
    repository.revoquerTousActifsDe(employeId);
    String valeur = UUID.randomUUID().toString();
    return repository.save(new QrCode(employeId, valeur));
  }

  @Transactional(readOnly = true)
  public Optional<QrCode> trouverActifDe(UUID employeId) {
    return repository.findByEmployeIdAndActifTrue(employeId);
  }

  /** Bloque le QR code actif d'un employé (ex. : employé désactivé). */
  public void bloquer(UUID employeId) {
    UUID bloquePar = CurrentUser.id().orElse(null);
    repository.findByEmployeIdAndActifTrue(employeId).ifPresent(qr -> qr.bloquer(bloquePar));
  }

  /**
   * Résout le QR code par sa valeur brute (appelé par le kiosque, EF-ATT-02). Retourne empty si le
   * code n'existe pas, est inactif ou bloqué.
   */
  @Transactional(readOnly = true)
  Optional<QrCode> resoudreParValeur(String valeur) {
    return repository.findByValeurAndActifTrue(valeur).filter(qr -> !qr.isBloque());
  }
}
