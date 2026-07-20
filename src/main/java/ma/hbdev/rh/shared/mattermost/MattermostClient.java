package ma.hbdev.rh.shared.mattermost;

import java.util.UUID;

public interface MattermostClient {
  ResultatMattermost envoyerMessagePrive(UUID destinataireId, String message);
}
