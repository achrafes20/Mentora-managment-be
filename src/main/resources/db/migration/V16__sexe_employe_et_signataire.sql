-- =====================================================================
--  V16__sexe_employe_et_signataire.sql
--  Migration Flyway — accord de genre sur le certificat de stage/travail généré
--  (document.CertificatGenerator) : le certificat s'accordait jusqu'ici toujours au masculin
--  ET au féminin à la fois ("il/elle"), faute de connaître le sexe de l'employé ou celui du
--  signataire RH. Deux colonnes indépendantes (modules employee/config distincts, jamais le même
--  type Postgres partagé entre deux modules — ai-instructions.md règle 2/6).
-- =====================================================================

-- Labels en MAJUSCULES : doivent matcher exactement le nom des constantes des enums Java
-- SexeEmploye/SexeSignataire (@JdbcTypeCode(SqlTypes.NAMED_ENUM) envoie le .name() littéral,
-- Postgres est sensible à la casse sur les labels d'un type ENUM).
CREATE TYPE sexe_employe     AS ENUM ('HOMME', 'FEMME');
CREATE TYPE sexe_signataire  AS ENUM ('HOMME', 'FEMME');

-- Nullable : aucune reprise de données réelle par migration (les fiches employé existantes
-- restent sans valeur tant que l'Admin RH ne la renseigne pas ; le certificat dégrade
-- gracieusement vers "il/elle" dans ce cas, cf. CertificatGenerator).
ALTER TABLE employes ADD COLUMN sexe sexe_employe;

-- EF-CFG-01 : identité du signataire RH des certificats générés, réutilisée sur le même principe
-- que raison_sociale/adresse (une seule ligne logique, gérée par l'Admin RH en Configuration).
ALTER TABLE identite_entreprise
    ADD COLUMN signataire_nom      VARCHAR(255),
    ADD COLUMN signataire_fonction VARCHAR(255),
    ADD COLUMN signataire_sexe     sexe_signataire;
