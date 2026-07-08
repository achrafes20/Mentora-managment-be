-- =====================================================================
--  V2__donnees_initiales.sql
--  Migration Flyway — Données de référence initiales
--  Insère les paramètres de configuration par défaut (EF-CFG-01)
--  et un compte Admin initial (à changer immédiatement après déploiement).
-- =====================================================================

-- =====================================================================
-- Paramètres de configuration (EF-CFG-01)
-- =====================================================================
INSERT INTO configuration_parametres (cle, valeur, description) VALUES
    ('delai_notification_fin_stage_jours_ouvrables', '3',
        'EF-DOC-02 : délai avant la fin de stage pour notifier l''Admin RH'),
    ('delai_notification_fin_cdd_jours_ouvrables', '15',
        'EF-DOC-13 : délai avant la fin de CDD pour notifier l''Admin RH'),
    ('delai_relance_fin_cdd_jours_ouvrables', '3',
        'EF-DOC-14 : relance si aucune désactivation engagée'),
    ('seuil_score_reactivation_candidature', '70',
        'EF-REC-12 : score minimal pour suggérer une réactivation'),
    ('fenetre_retention_candidature_mois', '6',
        'EF-REC-12 : fenêtre avant archivage automatique'),
    ('politique_verrouillage_compte', '{"tentatives_max": 5, "delai_deverrouillage_minutes": 30}',
        'EF-AUTH-09'),
    ('politique_mot_de_passe', '{"longueur_min": 10, "majuscule": true, "minuscule": true, "chiffre": true}',
        'NFR-SEC-08'),
    ('taille_max_fichier_octets', '10485760',
        'NFR-SEC-07 : limite de taille des fichiers uploadés (10 Mo)'),
    ('retention_notifications_in_app_jours', '90',
        'EF-NOTIF-05'),
    ('duree_session_inactivite_minutes', '30',
        'EF-AUTH-10');

-- =====================================================================
-- Compte Admin initial
-- Mot de passe : Admin@Mentora2025!  (bcrypt — à changer impérativement)
-- Hash généré avec bcrypt cost=12
-- =====================================================================
INSERT INTO utilisateurs (email, mot_de_passe_hash, role, nom, prenom, statut)
VALUES (
    'admin@hbdev.ma',
    '$2a$12$X5Y9GqMzQr6rHv1wJOiNZO7k/MuT3sDhD/dXwJwJLPKf0bXrIFbWm',
    'admin',
    'Admin',
    'HB Développement',
    'actif'
);
