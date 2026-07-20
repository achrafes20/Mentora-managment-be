package ma.hbdev.rh.notification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationMattermostRepository extends JpaRepository<NotificationMattermost, UUID> {}
