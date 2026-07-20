ALTER TABLE utilisateurs
    ADD COLUMN mattermost_user_id VARCHAR(64);

CREATE UNIQUE INDEX idx_utilisateurs_mattermost_user_id
    ON utilisateurs(mattermost_user_id)
    WHERE mattermost_user_id IS NOT NULL;
