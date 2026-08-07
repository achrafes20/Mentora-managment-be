package ma.hbdev.rh.attendance;

import java.util.List;
import java.util.UUID;
import ma.hbdev.rh.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** EF-ATT-17 : gestion des QR codes de site (Admin, ou délégué actif). */
@Service
@Transactional
class SiteQrCodeService {

  private final SiteQrCodeRepository repository;

  SiteQrCodeService(SiteQrCodeRepository repository) {
    this.repository = repository;
  }

  SiteQrCode generer(String libelle) {
    UUID creePar =
        CurrentUser.id()
            .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable."));
    String valeur = UUID.randomUUID().toString();
    return repository.save(new SiteQrCode(libelle, valeur, creePar));
  }

  @Transactional(readOnly = true)
  List<SiteQrCode> lister() {
    return repository.findAllByOrderByCreeLeDesc();
  }

  void desactiver(UUID id) {
    SiteQrCode site =
        repository.findById(id).orElseThrow(() -> new SiteQrCodeIntrouvableException(id));
    site.desactiver();
  }

  /** Résout un QR de site par sa valeur brute — vide si inconnu ou désactivé. */
  @Transactional(readOnly = true)
  java.util.Optional<SiteQrCode> resoudreParValeur(String valeur) {
    return repository.findByValeurAndActifTrue(valeur);
  }
}
