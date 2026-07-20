package ma.hbdev.rh.notification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
class NotificationIntrouvableException extends RuntimeException {
  NotificationIntrouvableException() {
    super("Notification introuvable");
  }
}
