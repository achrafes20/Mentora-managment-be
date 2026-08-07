-- EF-ATT-17 : QR code de site (affiché à l'entrée d'un lieu de travail) — l'employé le scanne
-- avec son propre téléphone pour pointer, ce qui prouve sa présence physique au lieu, en plus de
-- l'identité déjà prouvée par l'appairage de l'appareil (kiosque_activations.employe_id, V30).
CREATE TABLE sites_qr_codes (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    libelle     VARCHAR(100) NOT NULL,
    valeur      VARCHAR(255) NOT NULL UNIQUE,
    actif       BOOLEAN NOT NULL DEFAULT true,
    cree_par    UUID REFERENCES utilisateurs(id),
    cree_le     TIMESTAMPTZ NOT NULL DEFAULT now()
);
