package ma.hbdev.rh.document;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * L'envoi d'un document/certificat a échoué côté webhook n8n (timeout, connexion refusée, erreur
 * HTTP). 502 (pas 500) : la défaillance vient d'une dépendance externe, pas d'un bug applicatif —
 * distinction utile pour le frontend et pour la supervision. Ramassée automatiquement par {@link
 * ma.hbdev.rh.shared.web.GlobalExceptionHandler} via son gestionnaire générique, qui lit
 * l'annotation {@link ResponseStatus} pour exposer {@link #getMessage()} au lieu du message
 * générique réservé aux erreurs non identifiées.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
class EnvoiWebhookEchoueException extends RuntimeException {
  EnvoiWebhookEchoueException(String message, Throwable cause) {
    super(message, cause);
  }
}
