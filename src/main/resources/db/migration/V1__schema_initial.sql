-- =====================================================================
--  V1__schema_initial.sql
--  Migration Flyway — Schéma initial PostgreSQL 14+
--  Source : docs/schema_v1.sql
--  NE PAS MODIFIER manuellement après application en dev/preprod/prod.
--  Toute évolution = nouvelle migration V2__, V3__, etc.
-- =====================================================================

-- Extensions requises
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- recherche texte libre

-- =====================================================================
-- 0. TYPES ÉNUMÉRÉS
-- =====================================================================

CREATE TYPE role_utilisateur           AS ENUM ('admin', 'manager');
CREATE TYPE statut_actif_inactif       AS ENUM ('actif', 'inactif');
CREATE TYPE type_contrat_employe       AS ENUM ('CDI', 'CDD', 'STAGIAIRE', 'STAGIAIRE_REMUNERE');
CREATE TYPE type_scan_pointage         AS ENUM ('entree', 'sortie');
CREATE TYPE type_anomalie_pointage     AS ENUM ('retard', 'depart_anticipe', 'absence_checkout', 'presence_incomplete');
CREATE TYPE statut_offre_emploi        AS ENUM ('ouverte', 'fermee');
CREATE TYPE statut_candidature         AS ENUM
    ('recu', 'preselectionne', 'entretien', 'decision', 'embauche', 'rejete',
     'en_attente', 'suggestion_reactivation', 'archivee', 'non_traite');
CREATE TYPE resultat_entretien         AS ENUM ('favorable', 'defavorable');
CREATE TYPE source_candidature         AS ENUM ('indeed', 'linkedin', 'direct', 'email');
CREATE TYPE statut_analyse_ia          AS ENUM ('en_attente', 'en_cours', 'succes', 'echec');
CREATE TYPE type_demande_administrative AS ENUM ('conge', 'bon_sortie', 'document_libre', 'autre');
CREATE TYPE granularite_conge          AS ENUM ('journee', 'demi_matin', 'demi_apres_midi');
CREATE TYPE statut_demande_administrative AS ENUM ('en_attente', 'approuvee', 'rejetee', 'annulee');
CREATE TYPE type_mouvement_conge       AS ENUM ('initialisation', 'consommation', 'recredit', 'ajustement');
CREATE TYPE type_document_rh           AS ENUM ('certificat_stage', 'certificat_travail', 'document_libre', 'email_rejet_candidature');
CREATE TYPE motif_depart_employe       AS ENUM ('demission', 'licenciement', 'fin_cdd', 'rupture', 'autre');
CREATE TYPE statut_notification_planifiee AS ENUM ('planifiee', 'envoyee', 'relancee', 'annulee');
CREATE TYPE type_fin_surveillee        AS ENUM ('fin_stage', 'fin_cdd');
CREATE TYPE statut_delegation          AS ENUM ('active', 'revoquee', 'expiree');
CREATE TYPE statut_antivirus_fichier   AS ENUM ('en_attente', 'propre', 'rejete');
CREATE TYPE module_audit               AS ENUM
    ('authentification', 'employe', 'presence', 'recrutement',
     'demande_administrative', 'document', 'configuration', 'delegation', 'notification');

-- =====================================================================
-- Trigger générique : mise à jour automatique de modifie_le
-- =====================================================================
CREATE OR REPLACE FUNCTION maj_horodatage_modification()
RETURNS TRIGGER AS $$
BEGIN
    NEW.modifie_le = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- 1. AUTHENTIFICATION & RBAC
-- =====================================================================

CREATE TABLE utilisateurs (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                           VARCHAR(255) NOT NULL UNIQUE,
    mot_de_passe_hash               VARCHAR(255) NOT NULL,
    role                            role_utilisateur NOT NULL,
    nom                             VARCHAR(100) NOT NULL,
    prenom                          VARCHAR(100) NOT NULL,
    statut                          statut_actif_inactif NOT NULL DEFAULT 'actif',
    tentatives_echouees_consecutives INT NOT NULL DEFAULT 0,
    verrouille_jusqu_a              TIMESTAMPTZ,
    derniere_connexion_le           TIMESTAMPTZ,
    cree_le                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    modifie_le                      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_utilisateurs_modifie_le
    BEFORE UPDATE ON utilisateurs
    FOR EACH ROW EXECUTE FUNCTION maj_horodatage_modification();

CREATE TABLE tentatives_connexion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_saisi     VARCHAR(255) NOT NULL,
    utilisateur_id  UUID REFERENCES utilisateurs(id) ON DELETE SET NULL,
    reussie         BOOLEAN NOT NULL,
    adresse_ip      INET,
    horodatage      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tentatives_connexion_email ON tentatives_connexion(email_saisi, horodatage);

CREATE TABLE sessions_utilisateur (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id          UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    jeton_hash              VARCHAR(255) NOT NULL,
    expire_le               TIMESTAMPTZ NOT NULL,
    derniere_activite_le    TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoque_le              TIMESTAMPTZ,
    cree_le                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sessions_utilisateur ON sessions_utilisateur(utilisateur_id, revoque_le);

CREATE TABLE reinitialisations_mot_de_passe (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id  UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL,
    expire_le       TIMESTAMPTZ NOT NULL,
    utilise         BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE delegations_approbation (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_delegant_id   UUID NOT NULL REFERENCES utilisateurs(id),
    delegue_id          UUID NOT NULL REFERENCES utilisateurs(id),
    date_debut          DATE NOT NULL,
    date_fin            DATE NOT NULL,
    statut              statut_delegation NOT NULL DEFAULT 'active',
    revoque_par         UUID REFERENCES utilisateurs(id),
    revoque_le          TIMESTAMPTZ,
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_delegation_dates CHECK (date_fin >= date_debut),
    CONSTRAINT chk_delegation_distincte CHECK (admin_delegant_id <> delegue_id)
);
CREATE INDEX idx_delegations_actives ON delegations_approbation(statut, date_debut, date_fin);

-- =====================================================================
-- 2. FICHIERS
-- =====================================================================

CREATE TABLE fichiers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom_original        VARCHAR(255) NOT NULL,
    chemin_stockage     TEXT NOT NULL,
    type_mime           VARCHAR(150) NOT NULL,
    taille_octets       BIGINT NOT NULL,
    statut_antivirus    statut_antivirus_fichier NOT NULL DEFAULT 'en_attente',
    motif_rejet         TEXT,
    televerse_par       UUID REFERENCES utilisateurs(id),
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- 3. DÉPARTEMENTS
-- =====================================================================

CREATE TABLE departements (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom         VARCHAR(150) NOT NULL UNIQUE,
    manager_id  UUID REFERENCES utilisateurs(id),
    statut      statut_actif_inactif NOT NULL DEFAULT 'actif',
    cree_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    modifie_le  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_departements_modifie_le
    BEFORE UPDATE ON departements
    FOR EACH ROW EXECUTE FUNCTION maj_horodatage_modification();

-- =====================================================================
-- 4. DOSSIER EMPLOYÉ
-- =====================================================================

CREATE TABLE employes (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom                         VARCHAR(100) NOT NULL,
    prenom                      VARCHAR(100) NOT NULL,
    email                       VARCHAR(255) UNIQUE,
    telephone                   VARCHAR(30),
    poste                       VARCHAR(150),
    departement_id              UUID NOT NULL REFERENCES departements(id),
    manager_id                  UUID REFERENCES utilisateurs(id),
    utilisateur_id              UUID UNIQUE REFERENCES utilisateurs(id),
    date_embauche               DATE NOT NULL,
    type_contrat                type_contrat_employe NOT NULL,
    date_fin_contrat_prevue     DATE,
    date_depart                 DATE,
    motif_depart                motif_depart_employe,
    statut                      statut_actif_inactif NOT NULL DEFAULT 'actif',
    photo_url                   TEXT,
    candidature_origine_id      UUID,
    cree_le                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    modifie_le                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_fin_contrat_cdd_uniquement
        CHECK (type_contrat = 'CDD' OR date_fin_contrat_prevue IS NULL)
);
CREATE TRIGGER trg_employes_modifie_le
    BEFORE UPDATE ON employes
    FOR EACH ROW EXECUTE FUNCTION maj_horodatage_modification();

CREATE INDEX idx_employes_departement ON employes(departement_id);
CREATE INDEX idx_employes_manager ON employes(manager_id);
CREATE INDEX idx_employes_statut ON employes(statut);
CREATE INDEX idx_employes_utilisateur ON employes(utilisateur_id);
CREATE INDEX idx_employes_fin_contrat_cdd ON employes(date_fin_contrat_prevue) WHERE type_contrat = 'CDD';
CREATE INDEX idx_employes_recherche_trgm ON employes
    USING gin ((coalesce(nom,'') || ' ' || coalesce(prenom,'') || ' ' || coalesce(email,'')) gin_trgm_ops);

CREATE TABLE employe_transferts (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id              UUID NOT NULL REFERENCES employes(id) ON DELETE CASCADE,
    ancien_departement_id   UUID REFERENCES departements(id),
    nouveau_departement_id  UUID REFERENCES departements(id),
    ancien_manager_id       UUID REFERENCES utilisateurs(id),
    nouveau_manager_id      UUID REFERENCES utilisateurs(id),
    date_effet              DATE NOT NULL,
    effectue_par            UUID REFERENCES utilisateurs(id),
    cree_le                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE employe_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id      UUID NOT NULL REFERENCES employes(id) ON DELETE CASCADE,
    fichier_id      UUID NOT NULL REFERENCES fichiers(id),
    type_document   VARCHAR(100),
    televerse_par   UUID REFERENCES utilisateurs(id),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cartes_employe (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id              UUID NOT NULL REFERENCES employes(id) ON DELETE CASCADE,
    fichier_numerique_id    UUID REFERENCES fichiers(id),
    fichier_imprimable_id   UUID REFERENCES fichiers(id),
    lot_generation_id       UUID,
    genere_par              UUID REFERENCES utilisateurs(id),
    genere_le               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cartes_employe_employe ON cartes_employe(employe_id);

-- =====================================================================
-- 5. PRÉSENCE
-- =====================================================================

CREATE TABLE horaires_reference (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    heure_debut_matin           TIME NOT NULL DEFAULT '08:30',
    heure_fin_matin             TIME NOT NULL DEFAULT '13:00',
    heure_debut_apres_midi      TIME NOT NULL DEFAULT '14:00',
    heure_fin_apres_midi        TIME NOT NULL DEFAULT '17:00',
    tolerance_minutes           INT NOT NULL DEFAULT 10,
    date_effet                  DATE NOT NULL,
    cree_par                    UUID REFERENCES utilisateurs(id),
    cree_le                     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_horaires_reference_date_effet ON horaires_reference(date_effet DESC);

CREATE TABLE qr_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id      UUID NOT NULL REFERENCES employes(id),
    valeur          VARCHAR(255) NOT NULL UNIQUE,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,
    bloque          BOOLEAN NOT NULL DEFAULT FALSE,
    bloque_le       TIMESTAMPTZ,
    bloque_par      UUID REFERENCES utilisateurs(id),
    genere_le       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_qr_codes_employe ON qr_codes(employe_id, actif);

CREATE TABLE pointages (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id              UUID NOT NULL REFERENCES employes(id),
    qr_code_id              UUID NOT NULL REFERENCES qr_codes(id),
    type_scan               type_scan_pointage NOT NULL,
    horodatage              TIMESTAMPTZ NOT NULL,
    horaire_reference_id    UUID REFERENCES horaires_reference(id),
    corrige_manuellement    BOOLEAN NOT NULL DEFAULT FALSE,
    corrige_par             UUID REFERENCES utilisateurs(id),
    motif_correction        TEXT,
    cree_le                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pointages_employe_date ON pointages(employe_id, horodatage);
CREATE INDEX idx_pointages_qr_code ON pointages(qr_code_id);

CREATE TABLE anomalies_pointage (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id              UUID NOT NULL REFERENCES employes(id),
    date_pointage           DATE NOT NULL,
    type_anomalie           type_anomalie_pointage NOT NULL,
    pointage_entree_id      UUID REFERENCES pointages(id),
    pointage_sortie_id      UUID REFERENCES pointages(id),
    resolue                 BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_anomalies_employe_date ON anomalies_pointage(employe_id, date_pointage, resolue);

CREATE TABLE jours_feries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date_ferie      DATE NOT NULL UNIQUE,
    libelle         VARCHAR(150) NOT NULL,
    gere_par        UUID REFERENCES utilisateurs(id),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- 6. RECRUTEMENT
-- =====================================================================

CREATE TABLE offres_emploi (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intitule        VARCHAR(200) NOT NULL,
    description     TEXT,
    departement_id  UUID NOT NULL REFERENCES departements(id),
    statut          statut_offre_emploi NOT NULL DEFAULT 'ouverte',
    mots_cles_requis JSONB,
    cree_par        UUID REFERENCES utilisateurs(id),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now(),
    fermee_le       TIMESTAMPTZ
);

CREATE TABLE candidatures (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    offre_id                    UUID REFERENCES offres_emploi(id),
    nom                         VARCHAR(100),
    prenom                      VARCHAR(100),
    email                       VARCHAR(255) NOT NULL,
    telephone                   VARCHAR(30),
    intitule_poste_detecte      VARCHAR(200),
    source                      source_candidature NOT NULL DEFAULT 'email',
    cv_fichier_id               UUID REFERENCES fichiers(id),
    statut                      statut_candidature NOT NULL DEFAULT 'recu',
    analyse_courante_id         UUID,
    date_ingestion              TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_archivage              TIMESTAMPTZ,
    source_import_reference     TEXT,
    cree_le                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (offre_id, email)
);
CREATE INDEX idx_candidatures_statut ON candidatures(statut);
CREATE INDEX idx_candidatures_offre ON candidatures(offre_id);
CREATE INDEX idx_candidatures_recherche_trgm ON candidatures
    USING gin ((coalesce(nom,'') || ' ' || coalesce(prenom,'') || ' ' || email) gin_trgm_ops);

ALTER TABLE employes
    ADD CONSTRAINT fk_employes_candidature_origine
    FOREIGN KEY (candidature_origine_id) REFERENCES candidatures(id);

CREATE TABLE analyses_ia (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidature_id              UUID NOT NULL REFERENCES candidatures(id) ON DELETE CASCADE,
    statut                      statut_analyse_ia NOT NULL DEFAULT 'en_attente',
    extrait_prenom              VARCHAR(100),
    extrait_nom                 VARCHAR(100),
    extrait_email               VARCHAR(255),
    extrait_telephone           VARCHAR(30),
    extrait_intitule_poste      VARCHAR(200),
    score_correspondance        NUMERIC(5,2),
    annees_experience_estimees  NUMERIC(4,1),
    justification_score         TEXT,
    mots_cles                   JSONB,
    remplace_analyse_id         UUID REFERENCES analyses_ia(id),
    date_analyse                TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_analyses_ia_candidature ON analyses_ia(candidature_id, date_analyse DESC);

ALTER TABLE candidatures
    ADD CONSTRAINT fk_candidatures_analyse_courante
    FOREIGN KEY (analyse_courante_id) REFERENCES analyses_ia(id);

CREATE TABLE entretiens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidature_id      UUID NOT NULL REFERENCES candidatures(id) ON DELETE CASCADE,
    manager_id          UUID NOT NULL REFERENCES utilisateurs(id),
    resultat            resultat_entretien,
    commentaire         TEXT,
    date_entretien      TIMESTAMPTZ,
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entretiens_candidature ON entretiens(candidature_id);

-- =====================================================================
-- 7. DEMANDES ADMINISTRATIVES
-- =====================================================================

CREATE TABLE demandes_administratives (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id                  UUID NOT NULL REFERENCES employes(id),
    type_demande                type_demande_administrative NOT NULL,
    granularite                 granularite_conge,
    date_debut                  DATE,
    date_fin                    DATE,
    heure_depart                TIME,
    heure_retour_prevue         TIME,
    motif                       TEXT,
    fichier_document_libre_id   UUID REFERENCES fichiers(id),
    statut                      statut_demande_administrative NOT NULL DEFAULT 'en_attente',
    approuve_rejete_par         UUID REFERENCES utilisateurs(id),
    en_delegation               BOOLEAN NOT NULL DEFAULT FALSE,
    delegation_id               UUID REFERENCES delegations_approbation(id),
    date_decision               TIMESTAMPTZ,
    cree_par                    UUID REFERENCES utilisateurs(id),
    cree_le                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_conge_granularite CHECK (type_demande <> 'conge' OR granularite IS NOT NULL),
    CONSTRAINT chk_bon_sortie_horaires
        CHECK (type_demande <> 'bon_sortie' OR (heure_depart IS NOT NULL AND heure_retour_prevue IS NOT NULL))
);
CREATE INDEX idx_demandes_employe ON demandes_administratives(employe_id, statut, type_demande);
CREATE INDEX idx_demandes_periode ON demandes_administratives(date_debut, date_fin);

CREATE TABLE mouvements_conges (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id          UUID NOT NULL REFERENCES employes(id),
    demande_id          UUID REFERENCES demandes_administratives(id),
    type_mouvement      type_mouvement_conge NOT NULL,
    quantite_jours      NUMERIC(4,1) NOT NULL,
    date_mouvement      DATE NOT NULL DEFAULT CURRENT_DATE,
    commentaire         TEXT,
    cree_par            UUID REFERENCES utilisateurs(id),
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mouvements_conges_employe ON mouvements_conges(employe_id, date_mouvement);

-- =====================================================================
-- 8. DOCUMENTS RH
-- =====================================================================

CREATE TABLE envois_documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id          UUID REFERENCES employes(id),
    candidature_id      UUID REFERENCES candidatures(id),
    type_document       type_document_rh NOT NULL,
    fichier_id          UUID REFERENCES fichiers(id),
    destinataire_email  VARCHAR(255) NOT NULL,
    corps_message       TEXT,
    envoye_par          UUID REFERENCES utilisateurs(id),
    date_envoi          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_envoi_cible CHECK (employe_id IS NOT NULL OR candidature_id IS NOT NULL)
);
CREATE INDEX idx_envois_documents_employe ON envois_documents(employe_id, type_document);

CREATE TABLE notifications_planifiees (
    id                                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id                          UUID NOT NULL REFERENCES employes(id),
    type_surveillance                   type_fin_surveillee NOT NULL,
    date_echeance                       DATE NOT NULL,
    statut                              statut_notification_planifiee NOT NULL DEFAULT 'planifiee',
    premiere_notification_envoyee_le    TIMESTAMPTZ,
    relance_envoyee_le                  TIMESTAMPTZ,
    annulee_le                          TIMESTAMPTZ,
    cree_le                             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_planifiees_echeance
    ON notifications_planifiees(type_surveillance, date_echeance, statut);

-- =====================================================================
-- 8bis. NOTIFICATIONS MATTERMOST
-- =====================================================================

CREATE TABLE notifications_mattermost (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destinataire_id UUID NOT NULL REFERENCES utilisateurs(id),
    type_evenement  VARCHAR(100) NOT NULL,
    module          module_audit,
    entite_type     VARCHAR(100),
    entite_id       UUID,
    contenu         TEXT NOT NULL,
    envoyee_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
    echec           BOOLEAN NOT NULL DEFAULT FALSE,
    erreur          TEXT
);
CREATE INDEX idx_notifications_mattermost_destinataire ON notifications_mattermost(destinataire_id, envoyee_le);

-- =====================================================================
-- 9. NOTIFICATIONS IN-APP
-- =====================================================================

CREATE TABLE notifications_in_app (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destinataire_id     UUID NOT NULL REFERENCES utilisateurs(id),
    titre               VARCHAR(200) NOT NULL,
    message             TEXT NOT NULL,
    module              module_audit,
    lien_action         TEXT,
    entite_type         VARCHAR(100),
    entite_id           UUID,
    lu                  BOOLEAN NOT NULL DEFAULT FALSE,
    lu_le               TIMESTAMPTZ,
    mattermost_tente    BOOLEAN NOT NULL DEFAULT FALSE,
    mattermost_reussi   BOOLEAN,
    mattermost_erreur   TEXT,
    archivee_le         TIMESTAMPTZ,
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_in_app_destinataire ON notifications_in_app(destinataire_id, lu, cree_le);

-- =====================================================================
-- 10. CONFIGURATION
-- =====================================================================

CREATE TABLE configuration_parametres (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cle             VARCHAR(150) NOT NULL UNIQUE,
    valeur          JSONB NOT NULL,
    description     TEXT,
    modifie_par     UUID REFERENCES utilisateurs(id),
    modifie_le      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- 11. JOURNAL D'AUDIT
-- =====================================================================

CREATE TABLE journal_audit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id  UUID REFERENCES utilisateurs(id),
    action          VARCHAR(150) NOT NULL,
    module          module_audit NOT NULL,
    entite_type     VARCHAR(100),
    entite_id       UUID,
    details         JSONB,
    en_delegation   BOOLEAN NOT NULL DEFAULT FALSE,
    delegation_id   UUID REFERENCES delegations_approbation(id),
    adresse_ip      INET,
    horodatage      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_journal_audit_utilisateur ON journal_audit(utilisateur_id, horodatage);
CREATE INDEX idx_journal_audit_module ON journal_audit(module, horodatage);
CREATE INDEX idx_journal_audit_entite ON journal_audit(entite_type, entite_id);
CREATE INDEX idx_journal_audit_recherche_trgm ON journal_audit
    USING gin ((coalesce(action,'') || ' ' || coalesce(entite_type,'') || ' ' || coalesce(details::text,'')) gin_trgm_ops);

-- Journal en lecture seule (NFR-SEC-03 / EF-CFG-06)
CREATE RULE journal_audit_lecture_seule_update AS
    ON UPDATE TO journal_audit DO INSTEAD NOTHING;
CREATE RULE journal_audit_lecture_seule_delete AS
    ON DELETE TO journal_audit DO INSTEAD NOTHING;
