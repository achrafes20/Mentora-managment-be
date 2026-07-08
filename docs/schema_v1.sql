-- =====================================================================
--  GESTION ENTREPRISE — MODULE RH
--  Schéma SQL (PostgreSQL 14+)
--  Généré à partir de 01-requirements.md + 02-addendum-requirements.md
--  Chaque table/colonne est annotée avec le code d'exigence associé
--  (EF-xxx / NFR-xxx) pour garder la traçabilité conception -> base.
-- =====================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- recherche texte libre (EF-EMP-12, EF-REC-06, EF-CFG-04)

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

-- Fonction générique pour maintenir modifie_le à jour
CREATE OR REPLACE FUNCTION maj_horodatage_modification()
RETURNS TRIGGER AS $$
BEGIN
    NEW.modifie_le = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================================
-- 1. AUTHENTIFICATION & RBAC (EF-AUTH)
-- =====================================================================

CREATE TABLE utilisateurs (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                           VARCHAR(255) NOT NULL UNIQUE,
    mot_de_passe_hash               VARCHAR(255) NOT NULL,              -- bcrypt/argon2, NFR-SEC-02
    role                            role_utilisateur NOT NULL,          -- EF-AUTH-01/05
    nom                             VARCHAR(100) NOT NULL,
    prenom                          VARCHAR(100) NOT NULL,
    statut                          statut_actif_inactif NOT NULL DEFAULT 'actif',  -- EF-AUTH-07
    tentatives_echouees_consecutives INT NOT NULL DEFAULT 0,            -- EF-AUTH-09
    verrouille_jusqu_a              TIMESTAMPTZ,                        -- EF-AUTH-09
    derniere_connexion_le           TIMESTAMPTZ,
    cree_le                         TIMESTAMPTZ NOT NULL DEFAULT now(),
    modifie_le                      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_utilisateurs_modifie_le
    BEFORE UPDATE ON utilisateurs
    FOR EACH ROW EXECUTE FUNCTION maj_horodatage_modification();

-- EF-AUTH-06 : journal des tentatives de connexion (réussies/échouées)
CREATE TABLE tentatives_connexion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email_saisi     VARCHAR(255) NOT NULL,
    utilisateur_id  UUID REFERENCES utilisateurs(id) ON DELETE SET NULL,
    reussie         BOOLEAN NOT NULL,
    adresse_ip      INET,
    horodatage      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tentatives_connexion_email ON tentatives_connexion(email_saisi, horodatage);

-- EF-AUTH-10 : session avec expiration/inactivité + logout explicite
CREATE TABLE sessions_utilisateur (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id          UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    jeton_hash              VARCHAR(255) NOT NULL,   -- JWT ou équivalent, NFR-SEC-04
    expire_le               TIMESTAMPTZ NOT NULL,
    derniere_activite_le    TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoque_le              TIMESTAMPTZ,             -- logout explicite
    cree_le                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sessions_utilisateur ON sessions_utilisateur(utilisateur_id, revoque_le);

-- EF-AUTH-08 : réinitialisation de mot de passe par lien à durée limitée
CREATE TABLE reinitialisations_mot_de_passe (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id  UUID NOT NULL REFERENCES utilisateurs(id) ON DELETE CASCADE,
    token_hash      VARCHAR(255) NOT NULL,
    expire_le       TIMESTAMPTZ NOT NULL,
    utilise         BOOLEAN NOT NULL DEFAULT FALSE,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- EF-AUTH-11..15 (addendum) : délégation temporaire des droits d'approbation
-- N'introduit pas de nouveau rôle : porte sur une période de validité (délégué = Admin ou Manager existant).
CREATE TABLE delegations_approbation (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_delegant_id   UUID NOT NULL REFERENCES utilisateurs(id),      -- Admin principal
    delegue_id          UUID NOT NULL REFERENCES utilisateurs(id),      -- Admin secondaire ou Manager élevé
    date_debut          DATE NOT NULL,
    date_fin            DATE NOT NULL,
    statut              statut_delegation NOT NULL DEFAULT 'active',    -- expire automatiquement (EF-AUTH-13)
    revoque_par         UUID REFERENCES utilisateurs(id),
    revoque_le          TIMESTAMPTZ,
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_delegation_dates CHECK (date_fin >= date_debut),
    CONSTRAINT chk_delegation_distincte CHECK (admin_delegant_id <> delegue_id)
    -- NB EF-AUTH-12 : si delegue.role = 'manager', l'application ne doit accorder QUE les droits
    -- d'approbation (EF-ADM-02) et de décision recrutement — jamais EF-CFG / gestion comptes / désactivation.
);
CREATE INDEX idx_delegations_actives ON delegations_approbation(statut, date_debut, date_fin);

-- =====================================================================
-- 2. FICHIERS (upload générique — NFR-SEC-07)
-- =====================================================================

CREATE TABLE fichiers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom_original        VARCHAR(255) NOT NULL,
    chemin_stockage     TEXT NOT NULL,
    type_mime           VARCHAR(150) NOT NULL,
    taille_octets       BIGINT NOT NULL,
    statut_antivirus    statut_antivirus_fichier NOT NULL DEFAULT 'en_attente',  -- NFR-SEC-07
    motif_rejet         TEXT,
    televerse_par       UUID REFERENCES utilisateurs(id),
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- 3. DÉPARTEMENTS (EF-EMP-10)
-- =====================================================================

CREATE TABLE departements (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom         VARCHAR(150) NOT NULL UNIQUE,
    manager_id  UUID REFERENCES utilisateurs(id),   -- doit référencer un utilisateur role='manager' (contrôle applicatif)
    statut      statut_actif_inactif NOT NULL DEFAULT 'actif',
    cree_le     TIMESTAMPTZ NOT NULL DEFAULT now(),
    modifie_le  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_departements_modifie_le
    BEFORE UPDATE ON departements
    FOR EACH ROW EXECUTE FUNCTION maj_horodatage_modification();

-- =====================================================================
-- 4. DOSSIER EMPLOYÉ (EF-EMP)
-- =====================================================================

CREATE TABLE employes (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom                         VARCHAR(100) NOT NULL,
    prenom                      VARCHAR(100) NOT NULL,
    email                       VARCHAR(255) UNIQUE,
    telephone                   VARCHAR(30),
    poste                       VARCHAR(150),
    departement_id              UUID NOT NULL REFERENCES departements(id),
    manager_id                  UUID REFERENCES utilisateurs(id),        -- un seul manager direct (EF-EMP-06)
    utilisateur_id               UUID UNIQUE REFERENCES utilisateurs(id),  -- lien optionnel "is linked to" 0..1 (§2.1 diagramme de classes) :
                                                                            -- identité de connexion éventuellement associée à cette fiche RH,
                                                                            -- indépendant du rattachement hiérarchique (manager_id) ci-dessus.
    date_embauche               DATE NOT NULL,
    type_contrat                type_contrat_employe NOT NULL,
    date_fin_contrat_prevue     DATE,                                    -- EF-EMP-15 (CDD uniquement)
    date_depart                 DATE,                                    -- EF-DOC-08 (date de départ effective)
    motif_depart                motif_depart_employe,                    -- EF-DOC-08
    statut                      statut_actif_inactif NOT NULL DEFAULT 'actif',
    photo_url                   TEXT,
    candidature_origine_id      UUID,                                    -- lien EF-EMP-05 / EF-REC-13 (FK ajoutée plus bas)
    cree_le                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    modifie_le                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_fin_contrat_cdd_uniquement
        CHECK (type_contrat = 'CDD' OR date_fin_contrat_prevue IS NULL)  -- EF-EMP-15
);
CREATE TRIGGER trg_employes_modifie_le
    BEFORE UPDATE ON employes
    FOR EACH ROW EXECUTE FUNCTION maj_horodatage_modification();

CREATE INDEX idx_employes_departement ON employes(departement_id);
CREATE INDEX idx_employes_manager ON employes(manager_id);
CREATE INDEX idx_employes_statut ON employes(statut);
CREATE INDEX idx_employes_utilisateur ON employes(utilisateur_id);
CREATE INDEX idx_employes_fin_contrat_cdd ON employes(date_fin_contrat_prevue) WHERE type_contrat = 'CDD';
-- EF-EMP-04 / EF-EMP-12 : recherche texte libre nom/prénom/e-mail
CREATE INDEX idx_employes_recherche_trgm ON employes
    USING gin ((coalesce(nom,'') || ' ' || coalesce(prenom,'') || ' ' || coalesce(email,'')) gin_trgm_ops);

-- EF-EMP-11 : traçabilité des transferts département/manager
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

-- EF-EMP-03 : pièces jointes de la fiche employé (contrat, pièce d'identité...)
CREATE TABLE employe_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id      UUID NOT NULL REFERENCES employes(id) ON DELETE CASCADE,
    fichier_id      UUID NOT NULL REFERENCES fichiers(id),
    type_document   VARCHAR(100),          -- 'contrat', 'piece_identite', 'autre'
    televerse_par   UUID REFERENCES utilisateurs(id),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- EF-EMP-09 / EF-EMP-14 : carte employé (numérique + imprimable), génération unique + regénération/lot
CREATE TABLE cartes_employe (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id              UUID NOT NULL REFERENCES employes(id) ON DELETE CASCADE,
    fichier_numerique_id    UUID REFERENCES fichiers(id),
    fichier_imprimable_id   UUID REFERENCES fichiers(id),   -- PDF 85x54mm
    lot_generation_id       UUID,                            -- regroupe une génération en lot (EF-EMP-14)
    genere_par              UUID REFERENCES utilisateurs(id),
    genere_le               TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_cartes_employe_employe ON cartes_employe(employe_id);

-- =====================================================================
-- 5. PRÉSENCE (EF-ATT)
-- =====================================================================

-- EF-ATT-07 : horaire de référence, historisé (s'applique aux pointages futurs uniquement)
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

-- EF-ATT-01 : QRCode séparé de Employee (§2.4 diagramme de classes) — cycle de vie propre
-- (actif/bloqué/régénéré), l'ancien QR reste dans l'historique bloqué plutôt que d'être écrasé.
CREATE TABLE qr_codes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id      UUID NOT NULL REFERENCES employes(id),
    valeur          VARCHAR(255) NOT NULL UNIQUE,
    actif           BOOLEAN NOT NULL DEFAULT TRUE,          -- un seul QR actif à la fois par employé (contrôle applicatif)
    bloque          BOOLEAN NOT NULL DEFAULT FALSE,
    bloque_le       TIMESTAMPTZ,
    bloque_par      UUID REFERENCES utilisateurs(id),
    genere_le       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_qr_codes_employe ON qr_codes(employe_id, actif);

-- EF-ATT-02 : 2 scans par jour maximum. Référence le QR utilisé au scan (pas directement l'employé)
-- pour pouvoir tracer un scan effectué avec un QR ultérieurement bloqué (détection d'abus, §2.4).
CREATE TABLE pointages (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id              UUID NOT NULL REFERENCES employes(id),   -- dénormalisé pour les requêtes d'historique (EF-ATT-05)
    qr_code_id              UUID NOT NULL REFERENCES qr_codes(id),   -- QR effectivement scanné
    type_scan               type_scan_pointage NOT NULL,
    horodatage               TIMESTAMPTZ NOT NULL,               -- horodatage serveur fait foi (§3.2)
    horaire_reference_id    UUID REFERENCES horaires_reference(id),  -- horaire en vigueur au moment du scan
    corrige_manuellement    BOOLEAN NOT NULL DEFAULT FALSE,       -- EF-ATT-06
    corrige_par             UUID REFERENCES utilisateurs(id),
    motif_correction        TEXT,
    cree_le                 TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_pointages_employe_date ON pointages(employe_id, horodatage);
CREATE INDEX idx_pointages_qr_code ON pointages(qr_code_id);

-- EF-ATT-04 : anomalies (retard, départ anticipé, absence de check-out, présence incomplète)
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

-- EF-ADM-10 : jours fériés (gestion manuelle, pas d'API externe)
CREATE TABLE jours_feries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    date_ferie      DATE NOT NULL UNIQUE,
    libelle         VARCHAR(150) NOT NULL,
    gere_par        UUID REFERENCES utilisateurs(id),
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =====================================================================
-- 6. RECRUTEMENT (EF-REC)
-- =====================================================================

CREATE TABLE offres_emploi (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intitule        VARCHAR(200) NOT NULL,
    description     TEXT,
    departement_id  UUID NOT NULL REFERENCES departements(id),
    statut          statut_offre_emploi NOT NULL DEFAULT 'ouverte',
    mots_cles_requis JSONB,                          -- utilisé pour le matching EF-REC-12
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
    source                      source_candidature NOT NULL DEFAULT 'email',   -- EF-REC-03
    cv_fichier_id               UUID REFERENCES fichiers(id),
    statut                      statut_candidature NOT NULL DEFAULT 'recu',    -- pipeline EF-REC-07
    analyse_courante_id         UUID,                 -- pointeur vers la dernière AIAnalysis (FK ajoutée plus bas)
    date_ingestion               TIMESTAMPTZ NOT NULL DEFAULT now(),
    date_archivage               TIMESTAMPTZ,          -- EF-REC-12 (fenêtre de rétention dépassée)
    source_import_reference     TEXT,                  -- NFR-DATA-02 : traçabilité jusqu'à la source
    cree_le                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (offre_id, email)                            -- NFR-DATA-03 : déduplication par e-mail sur une offre
);
CREATE INDEX idx_candidatures_statut ON candidatures(statut);
CREATE INDEX idx_candidatures_offre ON candidatures(offre_id);
CREATE INDEX idx_candidatures_recherche_trgm ON candidatures
    USING gin ((coalesce(nom,'') || ' ' || coalesce(prenom,'') || ' ' || email) gin_trgm_ops);

ALTER TABLE employes
    ADD CONSTRAINT fk_employes_candidature_origine
    FOREIGN KEY (candidature_origine_id) REFERENCES candidatures(id);

-- AIAnalysis séparée de Application (§2.5 diagramme de classes) : une relance (retry()) crée une
-- nouvelle analyse plutôt que d'écraser l'ancienne — l'historique des échecs/succès est conservé,
-- et les champs extraits restent groupés pour la réutilisation au pré-remplissage (EF-EMP-05).
CREATE TABLE analyses_ia (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidature_id              UUID NOT NULL REFERENCES candidatures(id) ON DELETE CASCADE,
    statut                      statut_analyse_ia NOT NULL DEFAULT 'en_attente',   -- EF-REC-05
    extrait_prenom               VARCHAR(100),
    extrait_nom                  VARCHAR(100),
    extrait_email                 VARCHAR(255),
    extrait_telephone             VARCHAR(30),
    extrait_intitule_poste        VARCHAR(200),
    score_correspondance         NUMERIC(5,2),
    annees_experience_estimees   NUMERIC(4,1),
    justification_score          TEXT,
    mots_cles                    JSONB,                -- EF-REC-04(b), réutilisé pour EF-REC-12
    remplace_analyse_id          UUID REFERENCES analyses_ia(id),   -- chaîne de relances (retry())
    date_analyse                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_analyses_ia_candidature ON analyses_ia(candidature_id, date_analyse DESC);

ALTER TABLE candidatures
    ADD CONSTRAINT fk_candidatures_analyse_courante
    FOREIGN KEY (analyse_courante_id) REFERENCES analyses_ia(id);

-- EF-REC-08/09 : entretien conduit par le Manager du département concerné
CREATE TABLE entretiens (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    candidature_id      UUID NOT NULL REFERENCES candidatures(id) ON DELETE CASCADE,
    manager_id          UUID NOT NULL REFERENCES utilisateurs(id),
    resultat            resultat_entretien,
    commentaire         TEXT,
    date_entretien       TIMESTAMPTZ,
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entretiens_candidature ON entretiens(candidature_id);

-- =====================================================================
-- 7. DEMANDES ADMINISTRATIVES (EF-ADM)
-- =====================================================================

CREATE TABLE demandes_administratives (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id                  UUID NOT NULL REFERENCES employes(id),
    type_demande                type_demande_administrative NOT NULL,
    granularite                 granularite_conge,             -- congé uniquement
    date_debut                  DATE,
    date_fin                    DATE,
    heure_depart                TIME,                          -- bon de sortie
    heure_retour_prevue         TIME,                          -- bon de sortie
    motif                       TEXT,
    fichier_document_libre_id   UUID REFERENCES fichiers(id),  -- EF-ADM-09
    statut                      statut_demande_administrative NOT NULL DEFAULT 'en_attente',
    approuve_rejete_par         UUID REFERENCES utilisateurs(id),
    en_delegation               BOOLEAN NOT NULL DEFAULT FALSE,        -- EF-AUTH-14
    delegation_id               UUID REFERENCES delegations_approbation(id),
    date_decision                TIMESTAMPTZ,
    cree_par                    UUID REFERENCES utilisateurs(id),
    cree_le                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_conge_granularite CHECK (type_demande <> 'conge' OR granularite IS NOT NULL),
    CONSTRAINT chk_bon_sortie_horaires
        CHECK (type_demande <> 'bon_sortie' OR (heure_depart IS NOT NULL AND heure_retour_prevue IS NOT NULL))
);
CREATE INDEX idx_demandes_employe ON demandes_administratives(employe_id, statut, type_demande);
CREATE INDEX idx_demandes_periode ON demandes_administratives(date_debut, date_fin);

-- EF-ADM-03 : registre de mouvements (ledger) — solde = somme des mouvements
CREATE TABLE mouvements_conges (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id          UUID NOT NULL REFERENCES employes(id),
    demande_id          UUID REFERENCES demandes_administratives(id),
    type_mouvement      type_mouvement_conge NOT NULL,
    quantite_jours      NUMERIC(4,1) NOT NULL,     -- négatif = consommation, positif = crédit/ajustement
    date_mouvement      DATE NOT NULL DEFAULT CURRENT_DATE,
    commentaire         TEXT,
    cree_par            UUID REFERENCES utilisateurs(id),
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_mouvements_conges_employe ON mouvements_conges(employe_id, date_mouvement);
-- Le solde à l'instant T se calcule à la volée : SUM(quantite_jours) + accumulation (1,5j/mois, à la volée,
-- cf. date_embauche sur `employes`) — pas de champ stocké mutable (§3.4 confirmé).

-- =====================================================================
-- 8. DOCUMENTS RH (EF-DOC)
-- =====================================================================

-- Journal unifié des envois : certificat de stage, certificat de travail, document libre (EF-ADM-09),
-- e-mail de rejet de candidature (EF-REC-14) — tous suivent le même modèle de traçabilité (EF-DOC-06).
CREATE TABLE envois_documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id          UUID REFERENCES employes(id),
    candidature_id      UUID REFERENCES candidatures(id),      -- pour email_rejet_candidature
    type_document       type_document_rh NOT NULL,
    fichier_id          UUID REFERENCES fichiers(id),
    destinataire_email  VARCHAR(255) NOT NULL,
    corps_message       TEXT,
    envoye_par          UUID REFERENCES utilisateurs(id),
    date_envoi           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_envoi_cible CHECK (employe_id IS NOT NULL OR candidature_id IS NOT NULL)
);
CREATE INDEX idx_envois_documents_employe ON envois_documents(employe_id, type_document);

-- EF-DOC-01/02 (fin de stage) + EF-DOC-12/13/14 (addendum, fin de CDD) : surveillance planifiée n8n
CREATE TABLE notifications_planifiees (
    id                                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employe_id                          UUID NOT NULL REFERENCES employes(id),
    type_surveillance                   type_fin_surveillee NOT NULL,   -- fin_stage | fin_cdd
    date_echeance                       DATE NOT NULL,                  -- date de fin de stage/contrat surveillée
    statut                              statut_notification_planifiee NOT NULL DEFAULT 'planifiee',
    premiere_notification_envoyee_le    TIMESTAMPTZ,
    relance_envoyee_le                  TIMESTAMPTZ,   -- EF-DOC-14 : uniquement pour fin_cdd, à J-3
    annulee_le                          TIMESTAMPTZ,    -- EF-DOC-15 : annulée si date modifiée/désactivation
    cree_le                             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_planifiees_echeance
    ON notifications_planifiees(type_surveillance, date_echeance, statut);

-- =====================================================================
-- 8bis. NOTIFICATIONS MATTERMOST (classe Notification — package Cross-Cutting)
-- =====================================================================

-- Correspond à la classe `Notification` transversale du diagramme de classes : journalise
-- chaque envoi Mattermost ponctuel (nouvelle candidature en entretien EF-REC-08, décision sur
-- une demande EF-ADM-08, création de fiche employé EF-EMP-13, période de délégation EF-AUTH-15).
-- Distincte de `notifications_planifiees` (surveillance planifiée fin de stage/CDD) et de
-- `notifications_in_app` (canal de repli in-app, addendum EF-NOTIF).
CREATE TABLE notifications_mattermost (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destinataire_id UUID NOT NULL REFERENCES utilisateurs(id),
    type_evenement  VARCHAR(100) NOT NULL,   -- ex. 'candidature_entretien', 'demande_decidee', 'embauche_employe', 'delegation_debut'
    module          module_audit,
    entite_type     VARCHAR(100),
    entite_id       UUID,
    contenu         TEXT NOT NULL,
    envoyee_le      TIMESTAMPTZ NOT NULL DEFAULT now(),
    echec           BOOLEAN NOT NULL DEFAULT FALSE,   -- NFR-OPS-06 : indisponibilité Mattermost non bloquante
    erreur          TEXT
);
CREATE INDEX idx_notifications_mattermost_destinataire ON notifications_mattermost(destinataire_id, envoyee_le);

-- =====================================================================
-- 9. CENTRE DE NOTIFICATIONS IN-APP (EF-NOTIF — addendum, module 2.10)
-- =====================================================================

CREATE TABLE notifications_in_app (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    destinataire_id     UUID NOT NULL REFERENCES utilisateurs(id),
    titre               VARCHAR(200) NOT NULL,
    message             TEXT NOT NULL,
    module              module_audit,
    lien_action         TEXT,               -- EF-NOTIF-03 : redirection vers l'élément concerné
    entite_type         VARCHAR(100),
    entite_id           UUID,
    lu                  BOOLEAN NOT NULL DEFAULT FALSE,     -- EF-NOTIF-04
    lu_le               TIMESTAMPTZ,
    mattermost_tente    BOOLEAN NOT NULL DEFAULT FALSE,     -- EF-NOTIF-01/06
    mattermost_reussi   BOOLEAN,
    mattermost_erreur   TEXT,
    archivee_le         TIMESTAMPTZ,        -- EF-NOTIF-05 : rétention 90j (configurable) puis archivage
    cree_le             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_in_app_destinataire ON notifications_in_app(destinataire_id, lu, cree_le);

-- =====================================================================
-- 10. CONFIGURATION & PARAMÉTRAGE (EF-CFG)
-- =====================================================================

-- Table clé/valeur générique — regroupe tous les paramètres déjà modélisés ailleurs
-- (horaire de référence, jours fériés gérés via leur propre table, seuils recrutement,
-- politique de verrouillage, politique de mot de passe, délais de notification fin de stage/CDD).
CREATE TABLE configuration_parametres (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cle             VARCHAR(150) NOT NULL UNIQUE,
    valeur          JSONB NOT NULL,
    description     TEXT,
    modifie_par     UUID REFERENCES utilisateurs(id),
    modifie_le      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Valeurs par défaut (EF-CFG-01)
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
-- 11. JOURNAL D'AUDIT (NFR-SEC-03, EF-CFG-03/04/05/06, EF-AUTH-14)
-- =====================================================================

CREATE TABLE journal_audit (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    utilisateur_id  UUID REFERENCES utilisateurs(id),
    action          VARCHAR(150) NOT NULL,          -- ex. 'approbation_demande', 'modification_parametre'
    module          module_audit NOT NULL,          -- filtre EF-CFG-03
    entite_type     VARCHAR(100),
    entite_id       UUID,
    details         JSONB,
    en_delegation   BOOLEAN NOT NULL DEFAULT FALSE, -- EF-AUTH-14
    delegation_id   UUID REFERENCES delegations_approbation(id),
    adresse_ip      INET,
    horodatage      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_journal_audit_utilisateur ON journal_audit(utilisateur_id, horodatage);
CREATE INDEX idx_journal_audit_module ON journal_audit(module, horodatage);
CREATE INDEX idx_journal_audit_entite ON journal_audit(entite_type, entite_id);
-- EF-CFG-04 : recherche texte libre sur les détails et l'entité
CREATE INDEX idx_journal_audit_recherche_trgm ON journal_audit
    USING gin ((coalesce(action,'') || ' ' || coalesce(entite_type,'') || ' ' || coalesce(details::text,'')) gin_trgm_ops);

-- EF-CFG-06 : journal en lecture seule — aucune modification/suppression, même par l'Admin.
-- Les règles ci-dessous interceptent toute tentative d'UPDATE/DELETE au niveau base de données,
-- en complément du contrôle applicatif (défense en profondeur pour préserver la valeur probante).
CREATE RULE journal_audit_lecture_seule_update AS
    ON UPDATE TO journal_audit DO INSTEAD NOTHING;
CREATE RULE journal_audit_lecture_seule_delete AS
    ON DELETE TO journal_audit DO INSTEAD NOTHING;

COMMIT;

-- =====================================================================
-- NOTES DE CONCEPTION
-- =====================================================================
-- 1. Export & Reporting (EF-EXP) : aucune table dédiée — génération à la demande (EF-EXP-04),
--    exécutée par requêtes sur les tables ci-dessus (employes, pointages, demandes_administratives)
--    et matérialisée en Excel/PDF côté application, sans persistance en base.
-- 2. Le solde de congés (EF-ADM-03) n'est JAMAIS un champ stocké : il se calcule à la volée
--    en combinant `employes.date_embauche` (accumulation 1,5j/mois pour CDI/CDD, 0 pour stagiaires)
--    et la somme des `mouvements_conges.quantite_jours`, en excluant les jours de `jours_feries`
--    inclus dans une période de congé approuvée (§3.4).
-- 3. La contrainte « un Manager ne peut être désactivé tant que des employés actifs lui sont
--    rattachés » (EF-EMP-08) et « un département ne peut être désactivé s'il compte des employés
--    actifs » (EF-EMP-10) sont volontairement laissées au niveau applicatif (vérification avant
--    UPDATE ... SET statut='inactif'), car elles nécessitent une logique conditionnelle sur
--    l'ensemble des lignes liées, plus lisible et testable en couche service qu'en contrainte SQL/trigger.
-- 4. Le champ `employes.candidature_origine_id` matérialise EF-EMP-05/EF-REC-13 (création automatique
--    de fiche employé depuis une candidature "Embauché").
-- 5. Toutes les suppressions du système sont logiques (statut 'inactif'/'archivee'), jamais
--    physiques (NFR-DATA-01) — aucune table ci-dessus n'expose de suppression en cascade destructrice
--    sur les entités métier (employes, candidatures, departements).
-- 6. Alignement avec 03-class-diagram-README.md :
--    - `employes.utilisateur_id` matérialise l'association "is linked to" 0..1 entre User et
--      Employee (§2.1) — distincte du rattachement hiérarchique `manager_id`.
--    - `qr_codes` est une table séparée d'`employes` (§2.4) : cycle de vie propre (actif/bloqué),
--      régénération sans perte d'historique, et `pointages.qr_code_id` référence le QR scanné
--      plutôt que l'employé directement, pour détecter un scan effectué avec un QR déjà bloqué.
--    - `analyses_ia` est une table séparée de `candidatures` (§2.5) : une relance (retry()) crée
--      une nouvelle ligne plutôt que d'écraser la précédente, `candidatures.analyse_courante_id`
--      pointe vers la dernière analyse en date.
--    - `notifications_mattermost` correspond à la classe `Notification` du package Cross-Cutting :
--      journalise chaque envoi ponctuel (EF-REC-08, EF-ADM-08, EF-EMP-13, EF-AUTH-15), distincte
--      de `notifications_planifiees` (surveillance fin de stage/CDD) et `notifications_in_app`
--      (canal de repli in-app, addendum EF-NOTIF, non encore validé — cf. §4 du README).
--    - Divergences assumées (non corrigées, car normalisation raisonnable) : StageCertificate et
--      WorkCertificate restent unifiés dans `envois_documents` (type_document), et `Role` reste un
--      ENUM plutôt qu'une table, faute de comportement propre à modéliser pour ces deux cas.
