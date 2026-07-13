package ma.hbdev.rh.shared.file;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Point d'entrée unique pour toute feature qui a besoin de stocker un fichier (CV, pièces jointes
 * employé, documents libres). Quarantine le backend de stockage derrière cette interface — les
 * features consommatrices ne connaissent jamais le détail (disque local en dev, objet distant en
 * prod), même principe que {@code CvAnalysisProvider} pour l'IA.
 */
public interface FileStorageService {

  /**
   * Valide (MIME + taille — NFR-SEC-07, hors analyse antivirus, décision actée) puis stocke le
   * fichier.
   *
   * @throws FichierInvalideException si le fichier est rejeté
   */
  FichierUploade televerser(MultipartFile fichier, UUID televersePar);

  /** Métadonnées d'un fichier déjà stocké (jamais le contenu ni {@code cheminStockage}). */
  FichierUploade recuperer(UUID fichierId);

  /** Contenu du fichier, pour prévisualisation/téléchargement par la feature consommatrice. */
  Resource charger(UUID fichierId);
}
