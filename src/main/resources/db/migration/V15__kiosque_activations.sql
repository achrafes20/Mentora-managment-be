-- =====================================================================
--  V15__kiosque_activations.sql
--  NFR-UX-02 : remplace le permitAll() inconditionnel de /api/kiosque/** par un jeton
--  d'activation par appareil. Une ligne = un code généré par l'Admin (ou un délégué actif) ;
--  la même ligne devient l'enregistrement d'activation permanent de l'appareil une fois le code
--  saisi côté kiosque (statut passe de 'en_attente' à 'active', device_token_hash renseigné).
--  Pas d'expiration automatique pour l'instant (choix provisoire, cf. avancement-projet.md) :
--  seule une révocation manuelle (statut 'revoquee') ou, pour un code émis par un délégué, la fin
--  de la fenêtre de délégation (vérifiée à la lecture via DelegationApprobation.estEffectivementActive(),
--  jamais recalculée ici) invalide une activation.
--  tentatives_echouees_consecutives / verrouille_jusqu_a reprennent tel quel le mécanisme de
--  verrouillage déjà utilisé pour les comptes (utilisateurs, cf. AuthService) — même algorithme,
--  même config (app.security.lockout.*), appliqué ici au code en attente le plus récent faute
--  d'identifiant d'appareil connu avant qu'un code ne soit accepté.
-- =====================================================================

CREATE TYPE kiosque_activation_statut AS ENUM ('en_attente', 'active', 'revoquee');

CREATE TABLE kiosque_activations (
    id                                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_hash                           VARCHAR(255) NOT NULL,
    emis_par                            UUID NOT NULL REFERENCES utilisateurs(id),
    delegation_id                       UUID REFERENCES delegations_approbation(id),
    emis_le                             TIMESTAMPTZ NOT NULL DEFAULT now(),
    statut                              kiosque_activation_statut NOT NULL DEFAULT 'en_attente',
    device_token_hash                   VARCHAR(255),
    activee_le                          TIMESTAMPTZ,
    revoquee_le                         TIMESTAMPTZ,
    revoquee_par                        UUID REFERENCES utilisateurs(id),
    tentatives_echouees_consecutives    INT NOT NULL DEFAULT 0,
    verrouille_jusqu_a                  TIMESTAMPTZ
);

-- Résolution du jeton d'appareil à chaque scan kiosque (KiosqueActivationGuard) : un seul index,
-- utile uniquement une fois la ligne active (device_token_hash non nul).
CREATE UNIQUE INDEX idx_kiosque_activations_device_token
    ON kiosque_activations(device_token_hash) WHERE device_token_hash IS NOT NULL;

-- Recherche des codes encore candidats à une saisie (verification()) et de la ligne la plus
-- récente à pénaliser après une saisie erronée.
CREATE INDEX idx_kiosque_activations_en_attente
    ON kiosque_activations(statut, emis_le) WHERE statut = 'en_attente';

-- Écran de gestion (liste des appareils activés/révoqués par l'Admin).
CREATE INDEX idx_kiosque_activations_statut ON kiosque_activations(statut, emis_le);
