-- =====================================================================
--  V8__retrait_anciens_parametres_configuration.sql
--  Migration Flyway — Retrait des paramètres seedés par V2 (immuable, jamais modifiée).
--
--  Décision T4.B2 du 2026-07-24 (voir avancement-projet.md) : ces 10 valeurs (verrouillage de
--  compte, politique de mot de passe, timeout de session, taille max fichier, délais de
--  notification, seuils de recrutement) sont des constantes techniques/sécurité fixées au
--  déploiement, jamais de la politique RH qu'un Admin ferait évoluer en libre-service. Elles
--  deviennent des propriétés application.yml (surchargeables par variable d'environnement,
--  voir .env.example) ; ce retrait synchronise la base avec ce nouveau mode de configuration.
--
--  La table configuration_parametres elle-même n'est pas supprimée : action plus petite et
--  réversible, elle pourra accueillir de futurs paramètres réellement admin-éditables
--  (aucun aujourd'hui — l'identité de l'entreprise, EF-CFG-01 réécrit, a sa propre table).
-- =====================================================================

DELETE FROM configuration_parametres WHERE cle IN (
    'delai_notification_fin_stage_jours_ouvrables',
    'delai_notification_fin_cdd_jours_ouvrables',
    'delai_relance_fin_cdd_jours_ouvrables',
    'seuil_score_reactivation_candidature',
    'fenetre_retention_candidature_mois',
    'politique_verrouillage_compte',
    'politique_mot_de_passe',
    'taille_max_fichier_octets',
    'retention_notifications_in_app_jours',
    'duree_session_inactivite_minutes'
);
