# Avancement projet — Plan de réalisation & statut vivant

> **Rôle de ce fichier.** C'est à la fois le plan de réalisation séquencé et
> le statut vivant du projet. Ce fichier vit dans `rh-backend/docs/`, au même
> endroit que `ai-instructions.md` (le backend est le repo pivot — voir
> ai-instructions.md § Repository layout). Au début de chaque session de
> code, l'IA doit scanner ce fichier (avec `rh-backend/docs/ai-instructions.md`)
> pour se repérer. À la fin de chaque session : cocher ce qui est fait, noter
> ce qui est en cours ou bloqué dans la colonne statut de la tâche concernée.


---

## Règles de fonctionnement (à lire une fois, à respecter toujours)

1. **Slice verticale, mais deux repos = deux PRs.** Une tâche feature =
   schéma déjà en place (Flyway) + backend (controller/service/repository,
   `rh-backend`) + frontend (écrans, `rh-frontend`) + tests. Jamais de
   découpage horizontal backend/frontend entre les deux devs : la même
   personne porte les deux PRs de sa slice. **PR backend d'abord**
   (endpoint + tests + spec OpenAPI régénérée), **PR frontend le même jour**
   (types `api.ts` régénérés depuis cette spec + écrans). Une PR backend qui
   change une API ne reste jamais mergée seule sans que la PR frontend
   correspondante soit au moins ouverte.
2. **On ne touche pas hors de sa feature.** Si un besoin transverse apparaît
   (nouveau composant partagé, changement dans `shared/`), on le signale à
   l'autre et on décide ensemble — jamais de modification silencieuse.
3. **Definition of Done (DoD) commune à toutes les tâches feature :**
   - Endpoints sécurisés RBAC (admin/manager selon les EF concernées) ;
   - Tests unitaires + tests d'intégration Testcontainers si la DB est touchée ;
   - Événements d'audit émis pour les actions sensibles (NFR-SEC-03) ;
   - Écrans frontend fonctionnels branchés sur la vraie API (pas de mock) ;
   - Spec OpenAPI (`rh-backend/contracts/openapi.json`) et types
     `rh-frontend/src/types/api.ts` régénérés si un endpoint a changé ;
   - Les deux PRs (backend et frontend, quand la feature touche les deux
     repos) relues et approuvées par l'autre dev ;
   - Ce fichier mis à jour.
4. **Séquence avant vitesse.** Quand on termine sa tâche, on prend **la
   tâche suivante dans la séquence de sa propre colonne**, pas une tâche
   au hasard. Si on est bloqué par l'autre, on l'aide sur sa tâche (pair)
   plutôt que de sauter en avant.
5. **Priorité MoSCoW intégrée à la séquence.** Les Phases 0→3 couvrent
   l'intégralité des Must + le cœur des Should. Si le temps manque, tout
   ce qui suit la Phase 3 peut être coupé et le produit reste livrable.

**Légende statut :** `[ ]` à faire · `[~]` en cours · `[x]` fait ·
`[!]` bloqué (préciser par quoi).

---

## Phase 0 — Fondations (parallèle, aucune dépendance croisée)

*Objectif de sortie : dans un `workspace/` contenant les deux repos clonés
côte à côte, `cd rh-backend && cp .env.example .env && docker compose up`
fonctionne pour les deux devs (le compose référence `../rh-frontend`), et
la CI de **chaque repo** passe au vert sur une PR de test.*

### Achraf

- [x] **T0.A1 — Échafaudage du repo `rh-backend` (pivot).**
  Arborescence complète (`src/`, `n8n/`, `docs/`, `scripts/`,
  `contracts/`), migration des 3 documents (`ai-instructions.md`,
  `01-requirements.md`, ce fichier) dans `docs/`, `.gitignore`,
  `.env.example` commenté (inclut les variables partagées avec le
  frontend, ex. URL de l'API), `Makefile` squelette (`up`, `test`, `lint`,
  `n8n-export`, `n8n-import`), README section « Démarrage rapide »
  uniquement (le reste viendra en Phase 6, et mentionne qu'il faut aussi
  cloner `rh-frontend` en repo frère).
- [x] **T0.A2 — Squelette backend + migrations + export OpenAPI.**
  Projet Spring Boot (Java 17+, Maven wrapper), packages feature vides
  (`auth/`…`notification/` + `shared/`), `shared/web` (gestion d'erreurs
  globale, format de réponse, pagination), springdoc-openapi branché avec
  target Make (`make openapi-export`) qui écrit
  `rh-backend/contracts/openapi.json` — c'est le socle du contrat API que
  T0.B1 consomme. Flyway branché : `V1__schema_initial.sql`
  (= `schema_v1.sql` + bloc télétravail `telework-schema-addition.sql`),
  `V2__donnees_initiales.sql` (compte Admin, horaire de référence,
  paramètres par défaut, jours fériés civils fixes). Test d'intégration
  Testcontainers minimal qui démarre le contexte et rejoue les migrations
  sur un PostgreSQL 14 réel. Dockerfile multi-stage (stage `dev`), service
  `backend` + `postgres` dans docker-compose avec hot reload DevTools
  (source monté + note IDE dans le README).

### Taha

- [x] **T0.B1 — Échafaudage du repo `rh-frontend` + squelette Vite.**
  Repo séparé : `.gitignore`, README section « Démarrage
  rapide ». Vite + TypeScript + React Router (data router) + TanStack
  Query + Ant Design + react-hook-form/zod. `src/app/` : layout général
  (sidebar avec les entrées des 10 modules, désactivées tant que non
  construites), providers, router. `src/lib/` : client API (axios/fetch
  avec intercepteur token, gestion d'erreurs standard). Script
  `npm run generate:types` (openapi-typescript sur
  `../rh-backend/contracts/openapi.json` → `src/types/api.ts`, jamais
  édité à la main). Dockerfile stage `dev`, service `frontend` référencé
  depuis le docker-compose de `rh-backend` (le compose reste dans le repo
  pivot), HMR fonctionnel.
- [x] **T0.B2 — CI + qualité de code (les deux repos).**
  `rh-backend/.github/workflows/ci.yml` : Spotless+Checkstyle, tests,
  `trivy fs`. `rh-frontend/.github/workflows/ci.yml` :
  ESLint+Prettier, `tsc --noEmit`, Vitest, build, `trivy fs`.
  **Pas de CodeQL** (retiré — GitHub Advanced Security est payant par
  committer actif sur repo privé, hors budget ; cf. `ai-instructions.md`).
  Deux pipelines indépendants, chacun scopé à son propre repo (plus besoin
  de filtres `paths` puisqu'il n'y a plus qu'un seul type de code par
  repo). Configs lint/format commitées dans chaque repo et référencées
  dans `ai-instructions.md`. Hooks pre-commit (Husky + lint-staged côté
  `rh-frontend`, hook Maven [x] installé côté `rh-backend`). Logique dans `scripts/`
  (dupliquée ou partagée via un snippet commun documenté), YAML mince.
- [x] **T0.B3 — n8n + Mailpit + premier workflow SMTP.**
  Services `n8n` et `mailpit` dans le docker-compose de `rh-backend`.
  Workflow n8n minimal **webhook → SMTP (Mailpit)** exporté dans
  `rh-backend/n8n/workflows/` (valide la chaîne export/import et donne à
  la Phase 1 son canal e-mail). `rh-backend/n8n/README.md` : tableau
  workflow ↔ EF ↔ credentials, procédure `make n8n-export` /
  `make n8n-import`.

**🔒 Porte de phase 0 :** chacun clone **les deux repos** dans son
`workspace/` local à froid et vérifie que tout monte en une commande
depuis `rh-backend/`. CI verte sur les deux repos. Ensuite seulement,
Phase 1.

---

## Phase 1 — Les deux slices bloquantes : Auth & Employés

*Tout le reste du système dépend de ces deux modules. Un chacun.*

### Achraf — Slice Authentification (EF-AUTH, hors délégation)

- [ ] **T1.A1 — Auth de bout en bout.**
  Login JWT (`utilisateurs`, `sessions_utilisateur`), verrouillage après
  5 tentatives (`tentatives_connexion`, paramétrable via
  `configuration_parametres`), réinitialisation de mot de passe
  (`reinitialisations_mot_de_passe`, e-mail envoyé via le webhook n8n SMTP
  de T0.B3, visible dans Mailpit), filtres RBAC admin/manager
  (`shared/security`), gestion des comptes (création/désactivation
  admin & manager). Frontend : page login, contexte de session (rôle),
  guards de routes, écran gestion des comptes, écran reset.
  **La délégation d'approbation (EF-AUTH-11→15) est explicitement hors
  périmètre ici — c'est T4.B1.**
- [ ] **T1.A2 — Workflows `release.yml` (les deux repos).**
  `rh-backend/.github/workflows/release.yml` : build image multi-stage
  (stage prod, non-root), `trivy image`, push Docker Hub avec tags
  `sha-<court>` (immutable) / `latest`. `rh-frontend/.github/workflows/release.yml` :
  même schéma (stage prod nginx), sa propre image, son propre tag.
  Deux pipelines indépendants comme prévu par `ai-instructions.md`. Petit
  mais fait tôt : les images existent dès la Phase 1, HB peut commencer à
  regarder.

### Taha — Slice Départements + Employés (EF-EMP, cœur)

- [~] **T1.B1 — Départements + fichiers partagés.**
  CRUD `departements`. `shared/file` : service d'upload centralisé sur la
  table `fichiers` (validation MIME + taille ; **pas d'antivirus** —
  décision actée, le champ statut reste en base sans traitement).
  *(Backend + frontend construits et vérifiés bout en bout — voir Suivi de
  session pour ce qui reste : RBAC réel et blocage EF-EMP-10 différés.)*
- [ ] **T1.B2 — Dossier employé.**
  CRUD `employes` (fiche complète, types de contrat, `date_fin_contrat_prevue`
  CDD EF-EMP-15), désactivation logique avec motif (NFR-DATA-01), transferts
  historisés (`employe_transferts`, EF-EMP-11), pièces jointes
  (`employe_documents` via `shared/file`, EF-EMP-03). Frontend : liste avec
  recherche `pg_trgm` + filtres (EF-EMP-04/12), fiche détail (avec encart
  lecture seule « planning télétravail » vide pour l'instant — branché en
  T2.A1), formulaires création/édition, écran transfert.
  *Développé derrière un profil dev permit-all tant que T1.A1 n'est pas
  mergé.*

**🔒 Porte de phase 1 (tâche commune) :**
- [ ] **T1.C1 — Intégration sécurité.** Tous les endpoints existants passés
  sous RBAC réel, suppression du permit-all, test croisé (A teste le module
  de B et inversement).

---

## Phase 2 — Les Musts du go-live : Présence & Reprise de données

### Achraf — Slice Présence (EF-ATT complet, télétravail inclus)

- [ ] **T2.A1 — Pointage + télétravail.**
  Génération QR (`qr_codes`, un actif par employé, régénération/blocage),
  page kiosque publique de scan (entrée/sortie, horodatage serveur),
  calcul du temps de présence (pause 1h déduite, EF-ATT-03), moteur
  d'anomalies (retard/départ anticipé/absence checkout/présence incomplète)
  évalué contre `horaires_reference` **et court-circuité par
  `plannings_teletravail`** (EF-ATT-04/09 — la requête EXISTS de référence
  est documentée dans `telework-schema-addition.sql`), CRUD admin du
  planning télétravail + encart lecture seule sur la fiche employé
  (EF-ATT-08/10), écran historique des pointages (EF-ATT-05), écran
  anomalies (marquage résolu), gestion de l'horaire de référence
  (historisé, EF-ATT-07). Correction manuelle de pointage (EF-ATT-06,
  Could) : seulement si le reste est fini.

### Taha — Slice Import validé / Reprise Excel (EF-EMP-07 étendu)

- [ ] **T2.B1 — Import Excel/CSV avec dry-run.**
  Parsing + mapping de colonnes, validation ligne à ligne côté serveur,
  rapport d'erreurs détaillé (lignes rejetées + motifs, lignes valides
  traitées), mode simulation sans écriture, déduplication par matricule,
  idempotence (re-jouer = mettre à jour, pas dupliquer). Cibles couvertes :
  **départements, employés, soldes de congés initiaux** (lignes
  `mouvements_conges` type `initialisation` — la table existe depuis V1,
  pas besoin du module Demandes). Historique de présence : optionnel,
  seulement si demandé par la RH (à confirmer avec elle **maintenant**,
  pas en Phase 6). Frontend : assistant d'import (upload → dry-run →
  rapport → confirmation), journal des imports.
- [ ] **T2.B2 — (si T2.B1 finit avant T2.A1) Démarrer T3.B1.**

**🔒 Porte de phase 2 :** test de reprise avec un **vrai fichier Excel de la
RH** (même partiel/anonymisé). C'est le test qui révèle les surprises de
format — le faire maintenant, pas au go-live.

---

## Phase 3 — Notifications, Demandes administratives, Recrutement

*Recrutement est le module le plus lourd : Taha ne fait que ça.
Achraf construit d'abord le socle notifications dont les deux modules
ont besoin, puis les Demandes.*

### Achraf

- [ ] **T3.A1 — Socle notifications (cross-cutting).**
  Pattern événements Spring (`shared/` : publication) + trois écouteurs :
  `notifications_in_app` (créée systématiquement, EF-NOTIF-01),
  `notifications_mattermost` (client webhook `shared/mattermost`, échec
  journalisé sans bloquer l'in-app — EF-NOTIF-06, champ `mattermost_reussi`),
  audit (`journal_audit` en écriture, NFR-SEC-03 — les événements déjà émis
  par les modules des phases 1-2 sont branchés ici rétroactivement).
  Frontend : badge cloche + centre de notifications (liste, lu/non-lu,
  EF-NOTIF-02→05).
- [ ] **T3.A2 — Slice Demandes administratives (EF-ADM).**
  `demandes_administratives` (congé / bon de sortie / document libre,
  CHECK par type), workflow d'approbation manager→admin selon RBAC,
  ledger de congés (`mouvements_conges` : consommation à l'approbation,
  recrédit à l'annulation, solde toujours calculé — jamais stocké),
  gestion des jours fériés (EF-ADM-10, saisie manuelle des fêtes
  hégiriennes), événements de notification sur décision (EF-ADM-08).
  Frontend : formulaires de demande, file d'approbation, historique filtré
  (EF-ADM-06), registre de congés par employé, écran jours fériés.

### Taha

- [ ] **T3.B1 — Slice Recrutement (EF-REC).**
  Offres (`offres_emploi`, mots-clés matching), candidatures (pipeline de
  statuts complet, `UNIQUE (offre_id, email)` NFR-DATA-03), upload CV via
  `shared/file`, endpoint `/api/recruitment/ingest` + **workflow n8n IMAP →
  POST** (exporté dans `n8n/workflows/`), interface `CvAnalysisProvider` +
  implémentation Gemini (`shared/ai`, dégradation gracieuse si clé absente
  EF-REC-05, chaîne de relance `analyses_ia`/`remplace_analyse_id`),
  entretiens (`entretiens`, résultat + commentaires), e-mail de rejet
  (EF-REC-14 via webhook SMTP n8n), **événement `CandidatEmbauche` →
  création fiche employé pré-remplie** (EF-REC-13, consommé côté module
  employé — coordonner l'interface de l'événement avec Achraf), événements
  de notification (EF-REC-08). Frontend : liste des offres, pipeline
  candidatures (vue par étape), fiche candidature (CV, analyse IA, score,
  justification), écran entretien. *Mocker `CvAnalysisProvider` dans tous
  les tests — jamais la vraie clé en CI.*

**🔒 Porte de phase 3 — jalon « produit livrable ».** Tous les Must sont
couverts. Si le temps restant est court : sauter directement à la Phase 6.

---

## Phase 4 — Documents RH, Délégation, Configuration & Audit

### Achraf — Slice Documents RH (EF-DOC + EF-EMP-15/16)

- [ ] **T4.A1 — Certificats + surveillance planifiée.**
  Génération PDF certificat de stage / certificat de travail,
  `envois_documents` (journal unifié, renvoi manuel EF-DOC-07/11), envoi
  par e-mail via webhook SMTP n8n. `notifications_planifiees` : création
  à l'embauche stagiaire/CDD, règles J-3 (stage) / J-15 + relance J-3
  (CDD, EF-DOC-12→14), annulation si date modifiée ou employé désactivé
  (EF-DOC-15/16). **Workflow n8n cron quotidien →
  `/api/internal/surveillance/run`** (exporté). Envoi de document libre
  (EF-ADM-09). Frontend : écran documents par employé, file de
  surveillance visible.

### Taha

- [ ] **T4.B1 — Délégation d'approbation (EF-AUTH-11→15).**
  `delegations_approbation` (période, révocation, expiration), transfert
  effectif des droits d'approbation pendant la période, marquage
  `en_delegation`/`delegation_id` dans l'audit (EF-AUTH-14), notification
  de début/fin (EF-AUTH-15), bannière frontend chez le délégué (EF-DASH-05).
- [ ] **T4.B2 — Configuration & consultation d'audit (EF-CFG).**
  Écran admin des `configuration_parametres` (édition typée côté service),
  écran de consultation du `journal_audit` : filtres par module/utilisateur/
  période, recherche texte libre `pg_trgm` (EF-CFG-04), lecture seule
  garantie (les RULES SQL existent depuis V1 — vérifier par un test
  d'intégration qui tente un UPDATE et constate l'échec silencieux).

---

## Phase 5 — Dashboard & Export

### Achraf
- [ ] **T5.A1 — Tableau de bord (EF-DASH).** Agrégations lecture seule
  (effectifs, présence du jour incluant l'état télétravail, demandes en
  attente, pipeline recrutement), rafraîchies au chargement. Aucune table
  dédiée — requêtes sur l'existant.

### Taha
- [ ] **T5.B1 — Exports (EF-EXP).** Génération Excel/PDF à la demande
  (employés, feuille de présence — distinguant télétravail / absence
  réelle, EF-ATT-10 —, demandes), aucune persistance (EF-EXP-04).

### Reliquat optionnel (prendre seulement si tout le reste est fait)
- [ ] **T5.X1 — Cartes employé (EF-EMP-09/14)** : PDF 85×54 avec QR,
  génération unitaire + par lot. *(Dépend des QR de T2.A1.)*
- [ ] **T5.X2 — Correction manuelle de pointage (EF-ATT-06)** si non faite
  en T2.A1.

---

## Phase 6 — Go-live & transmission (les deux, ensemble)

- [ ] **T6.1 — Reprise de données réelle.** Import des fichiers Excel
  définitifs de la RH via l'assistant (dry-run → corrections RH → import),
  vérification croisée des soldes de congés avec la RH.
- [ ] **T6.2 — READMEs finaux « clone à froid ».** Un README par repo
  (`rh-backend` et `rh-frontend`), sections 3→6 complétées (carte du
  dépôt, tâches courantes, CI/CD en 5 lignes, dépannage alimenté par les
  vrais incidents rencontrés). Le README `rh-backend` explique aussi le
  clonage du repo frère (`workspace/` avec les deux repos côte à côte) et
  la référence `../rh-frontend` dans le compose. Test : un dev n'ayant
  jamais touché aux deux repos (ou l'IA en session vierge) les clone et
  suit les READMEs seul.
- [ ] **T6.3 — Dossier de déploiement.** Mettre à jour le PDF technique
  (technical-handoff.tex) avec les valeurs réelles (base OS des images,
  liste finale des variables d'env), le transmettre à l'équipe DevOps HB,
  répondre à leurs questions d'intégration.
- [ ] **T6.4 — Durcissement.** Rotation du mot de passe Admin de seed,
  passage en revue des `.trivyignore` (les deux repos), vérification
  qu'aucun secret n'a fuité dans l'historique Git (les deux repos), tag
  `v1.0.0` sur chaque repo → images Docker Hub versionnées.

---

## Suivi de session (à remplir en continu)

| Date | Dev | Tâche | Note (avancée, blocage, décision prise) |
|---|---|---|---|
| 2026-07-08 | Taha | T0.A1 | Arborescence (`src/`, `n8n/`, `docs/`, `scripts/`, `contracts/`), 5 docs migrés, `.gitignore`, `.env.example` commenté, README « Démarrage rapide » — faits en binôme. Reste pour Achraf : `Makefile` squelette (volontairement pas fait maintenant) + tout T0.A2. |
| 2026-07-08 | Taha | T0.B1 | Scaffold Vite + TS + React Router (data router) + TanStack Query + Ant Design (locale fr_FR) + react-hook-form/zod. `src/app/` (router, layout sidebar 10 modules désactivés), `src/lib/apiClient.ts` (intercepteurs token + erreurs), script `generate:types`, Dockerfile stage `dev` + `.dockerignore`, `.env.example` frontend (`VITE_API_BASE_URL`). `npm run build` et `npm run dev` vérifiés OK. Reste : intégration réelle avec le service `frontend` du docker-compose (Achraf, T0.A2) et un vrai `contracts/openapi.json` pour que `generate:types` produise quelque chose. |
| 2026-07-08 | Taha (+ IA) | — | Décision : convention des fichiers pointeurs natifs (`.github/copilot-instructions.md`, `.cursorrules`) supprimée de `ai-instructions.md` et des tâches T0.A1/T0.B1 — plus aucun fichier pointeur à créer dans aucun des deux repos. |
| 2026-07-08 | Taha | T0.B1 (complément) | Système de design HB Développement intégré : `src/app/theme.ts` (tokens réels — Encre Marine/Sauge/Ambre/Corail/Gris Dossier — mappés sur `ConfigProvider theme`), `src/styles/global.css` (polices Source Serif 4/IBM Plex Sans/Mono, variables `--rose-marque*` tenues hors des tokens sémantiques AntD, cf. règle d'usage restreinte). Specs UX Figma Make (5 fichiers `.md` + `theme.css`/`fonts.css`) archivées dans `docs/ui-design/` — référence copie/comportement d'écran, ne remplace pas `01-requirements.md` en cas de désaccord. Sidebar `modules.ts` vérifiée cohérente avec la nav du spec (Départements = onglet dans Employés, Audit = onglet dans Configuration) — aucun changement nécessaire. |
| 2026-07-08 | Taha | T0.B2 | Moitié frontend faite : ESLint (flat config) + Prettier (sans point-virgule, aligné au style déjà écrit) + `eslint-config-prettier`, Vitest + Testing Library + jsdom (2 tests de fumée), `.github/workflows/ci.yml` (`format:check`, `lint`, `typecheck`, `test`, `build`, jobs séparés CodeQL + `trivy fs`), Husky + lint-staged (hook pre-commit testé en conditions réelles). `engines`/`​.nvmrc` ajoutés (Node ≥22.12) suite à un warning `EBADENGINE` bénin. Deux accrocs de version résolus : `vitest@4` exigeait Vite 6/7 (conflit de types `Plugin` avec notre Vite 5.4 pinné) → `vitest` fixé en `^3.2.7` ; `jsdom@27` cassait (bug ESM/CJS d'une dépendance transitive de parsing CSS) → fixé en `^25`. Reste : moitié backend (`rh-backend/.github/workflows/ci.yml` Spotless+Checkstyle+tests+CodeQL+trivy, hook Maven) bloquée sur le `pom.xml` de T0.A2 côté Achraf — pas de sens à l'écrire avant que la config Spotless/Checkstyle existe réellement. |
| 2026-07-08 | Taha (+ IA) | T0.B2 | CodeQL retiré du CI frontend (job `codeql` supprimé de `ci.yml`) : sur repo privé, l'upload SARIF exige GitHub Advanced Security (licence payante par committer actif, Team/Enterprise) — confirmé via la doc GitHub, pas un simple réglage. Décision : hors budget pour ce projet, pas remplacé par un autre SAST pour l'instant ; on s'appuie sur les tests fonctionnels par module. Risque assumé : aucun outil n'analyse les patterns de vulnérabilité applicatifs (injection, XSS, etc.) — Trivy couvre seulement les CVE de dépendances. `ai-instructions.md` (§CI/CD) et T0.B2 mis à jour en conséquence ; **à revoir en T6.4 (durcissement)**, ex. ESLint security plugin ou Semgrep (gratuits, sans dépendance à Advanced Security). |
| 2026-07-08 | Taha (+ IA) | T0.B1 | Terminé : `contracts/openapi.json` généré pour de vrai via `make openapi-export` (contre le T0.A2 d'Achraf, maintenant mergé), `npm run generate:types` exécuté côté frontend → `src/types/api.ts` régénéré (plus un stub), `tsc -b` vérifié propre. Service `frontend` ajouté au `docker-compose.yml` du repo pivot (`build.context: ../Mentora-managment-fe`, bind mount source + volume anonyme `node_modules`, port 5173, `VITE_API_BASE_URL`). Stack complète vérifiée en conditions réelles : `docker compose up -d --build` démarre `db`+`api`+`frontend` ensemble, healthchecks verts, `/api/health` répond `OK`, frontend répond 200, `node_modules` bien peuplé dans le conteneur (bind mount ne l'écrase pas). |
| 2026-07-08 | Taha (+ IA) | T0.B2 | Terminé : `rh-backend/.github/workflows/ci.yml` ajouté (job `build-test` : `spotless:check` + `checkstyle:check` + `mvn verify` sur JDK 21/Temurin avec cache Maven ; job `trivy` : `trivy fs`, pas de CodeQL — cohérent avec la décision déjà actée côté frontend). Hook Maven pre-commit déjà en place depuis T0.A2, rien à ajouter. |
| 2026-07-08 | Taha (+ IA) | T0.B1 | Service `docker-compose.yml` renommé `api` → `backend` (`container_name: rh_api` → `rh_backend`) pour que le nom corresponde à ce que documentera le README (clone à froid cohérent). Stack complète re-testée après renommage : `docker compose up -d --build` → `rh_db`/`rh_backend`/`rh_frontend` tous up, healthcheck backend vert, `/api/health` → `OK`, frontend → 200. |
| 2026-07-08 | Taha (+ IA) | T0.B3 | Terminé : services `mailpit` (`axllent/mailpit:latest`, SMTP 1025 + UI web 8025) et `n8n` (`n8nio/n8n:latest`, port 5678, `GENERIC_TIMEZONE=Africa/Casablanca`, volume `rh_n8n_data` pour la persistance + bind mount `./n8n/workflows:/n8n/workflows`) ajoutés au `docker-compose.yml` du repo pivot. `.env.example` complété (`N8N_ENCRYPTION_KEY`, `N8N_HOST`, `N8N_WEBHOOK_URL`). Workflow minimal **Webhook (`/webhook/notify-email`) → Send Email (SMTP)** créé et testé bout en bout (webhook déclenché → e-mail reçu dans Mailpit, vérifié via son API `/api/v1/messages`), credential SMTP `Mailpit (dev)` créé côté n8n (host `mailpit`, port 1025, sans TLS/auth — jamais exporté, à recréer par chaque dev, voir `n8n/README.md`). Exporté dans `n8n/workflows/ZljnpSfmg1POyS7P.json` via `make n8n-export`. Chaîne export/import validée en conditions réelles : workflow archivé + supprimé, réimporté via `make n8n-import`, republié (`n8n publish:workflow`), re-testé — e-mail bien reçu une seconde fois. `n8n/README.md` écrit (tableau workflow↔EF↔credentials, procédure premier lancement, export/import). Stack à 5 services (`db`+`backend`+`frontend`+`mailpit`+`n8n`) vérifiée ensemble via un seul `docker compose up -d`, tous healthy. |
| 2026-07-08 | Taha (+ IA) | — | Deux autres petits bugs mécaniques trouvés et corrigés en cours de route (T0.A1, pas des choix de design) : (1) `Makefile` — cibles `n8n-export`/`n8n-import` sans le flag `--separate` requis par la CLI n8n pour un export/import par dossier (`--output=/n8n/workflows/` échouait avec « must be a writeable file ») ; corrigé (`--separate --pretty` à l'export, `--separate` à l'import). (2) Note d'environnement pour quiconque lance `make n8n-export`/`n8n-import` **depuis Git Bash sous Windows** : préfixer avec `MSYS_NO_PATHCONV=1`, sinon Git Bash réécrit `/n8n/workflows/` (chemin conteneur) en chemin Windows avant l'appel à `docker compose exec` — documenté dans `n8n/README.md`. Par ailleurs : `--activeState=fromJson` (réactivation auto à l'import) n'est **pas** supporté en mode déploiement standard (seulement queue/multi-main) — chaque workflow réimporté doit être réactivé manuellement dans l'UI n8n ; documenté aussi. |
| 2026-07-08 | Taha (+ IA) | — | Correction (relevée par Taha) : `mailpit`/`n8n` étaient sur tag `:latest` dans `docker-compose.yml`, incohérent avec `postgres:14-alpine` (seul service jusque-là avec une version figée). Épinglés sur les versions réellement testées cette session : `n8nio/n8n:2.29.8`, `axllent/mailpit:v1.30.3` (tags vérifiés existants et correspondant au digest déjà tiré). `README.md` du repo pivot mis à jour : tableau des 5 services + URLs d'accès + pointeur vers `n8n/README.md` pour le bootstrap n8n (le README ne mentionnait encore que backend+db+frontend). |
| 2026-07-08 | Taha (+ IA) | — | Deux bugs bloquants trouvés et corrigés en cours de route (mécaniques, pas des choix de design — corrigés directement) : (1) `application.yml` — `datasource`/`jpa`/`flyway`/`jackson` étaient imbriqués par erreur sous `app:` au lieu de `spring:` ; ça fonctionnait par accident en Docker (les variables d'env `SPRING_DATASOURCE_*` du compose sont bindées directement par Spring, indépendamment du YAML) mais cassait tout lancement hors Docker (`make openapi-export`, `mvnw` en local, IDE). (2) Fins de ligne CRLF sur tout l'arbre du repo backend (`mvnw` + tous les `.java`) — cassait le build Docker (`./mvnw: not found`, shebang `#!/bin/sh\r` illisible par Alpine) et faisait échouer `spotless:check` (Google Java Format exige LF). `.gitattributes` ajouté (`* text=auto eol=lf`, overrides `.cmd`/`.bat` en CRLF) + `mvnw` normalisé + `mvn spotless:apply` passé sur les 14 fichiers Java. **Point d'attention environnement (non corrigé, pas un bug projet) :** sur ce poste, Testcontainers ne joint pas Docker Desktop depuis Git Bash (`NpipeSocketClientProviderStrategy` échoue, souci connu MSYS/npipe) — `mvn verify` complet non validé en local pour cette raison ; `spotless:check`/`checkstyle:check` validés séparément, et le pipeline GitHub Actions (runners Ubuntu, socket Docker natif) n'est pas concerné. |
| 2026-07-10 | Taha (+ IA) | T1.B1 | Backend : `shared/file` (`Fichier`/`FichierRepository` package-private, `FileStorageService` public + `LocalDiskFileStorage` — disque local en dev via bind mount `./data/uploads`, MIME/taille validés, motif de rejet renvoyé — NFR-SEC-07 hors antivirus, actée) ; `employee` (`Departement`, `DepartementService`, `DepartementController` EF-EMP-10 : créer/modifier/désactiver, nom unique). Événement `DepartementModifieEvent` publié à chaque action (pour l'écouteur d'audit T3.A1, pas encore branché). Frontend : `EmployesPage` (onglet Départements fonctionnel + onglet Employés placeholder T1.B2), module `employees` activé dans la sidebar. `contracts/openapi.json` régénéré, `api.ts` régénéré, `tsc -b` propre des deux côtés. Vérifié bout en bout en réel (pas seulement tests) : CRUD complet via curl contre PostgreSQL 14 réel (create → 409 sur nom dupliqué → edit → deactivate → `statut: inactif` confirmé), stack complète (`db`+`backend`+`frontend`) démarrée via `docker compose up`. Tests : 4 tests unitaires `LocalDiskFileStorageTest`, 3 tests d'intégration Testcontainers `DepartementIntegrationTest` (CRUD, nom dupliqué → 409, id inconnu → 404) — tous verts ; Testcontainers a bien atteint Docker Desktop cette fois (contrairement au point d'attention noté à T0.A2). **Deux décisions actées avec Taha, à ne pas perdre de vue :** (1) le blocage EF-EMP-10 (désactivation bloquée si employés actifs rattachés) est **différé à T1.B2** — le module Employé (entité JPA sur `employes`) n'existe pas encore, la désactivation fonctionne aujourd'hui sans ce garde-fou ; (2) `/api/departements/**` est temporairement en `permitAll` dans `SecurityConfig` (T1.A1 pas encore mergé) — marqué d'un `TODO(T1.C1)`, à retirer à la porte de phase 1 avec RBAC admin réel. Branche `feat(departements)` sur `rh-backend`, convention de nommage alignée sur `feat(auth)` d'Achraf. PRs non ouvertes (Taha les gère lui-même). |
| 2026-07-08 | Taha (+ IA) | — | Premier push sur `origin/main` (les deux repos) → 3 échecs CI successifs côté backend, corrigés un par un : (1) hook pre-commit jamais déclenché lors du premier commit — `make install-hooks` n'avait jamais été lancé sur ce poste (`.git/hooks/pre-commit` n'existe pas par défaut, `scripts/pre-commit` versionné ne suffit pas) ; installé, et `README.md` complété d'une étape « à refaire à chaque clone » (contrairement au frontend où Husky s'auto-installe via le script `prepare` de `npm install`). (2) `build-test` : `./mvnw: Permission denied` (exit 126) — `mvnw` était tracké en mode `100644` depuis le tout premier commit (jamais `100755`) ; `core.fileMode=false` sur ce poste Windows masquait le problème en local (`chmod +x` jamais reflété dans l'index). Corrigé via `git update-index --chmod=+x mvnw`. (3) `trivy` : d'abord un 429 de Maven Central pendant la résolution du POM effectif (parent `spring-boot-starter-parent` + BOM `spring-data-bom`) — corrigé en pré-remplissant `~/.m2` (`./mvnw dependency:go-offline -q`) avant l'étape Trivy dans `ci.yml`. Une fois ce blocage levé, Trivy a pu tourner pour de vrai et a trouvé **26 vulnérabilités réelles (21 HIGH, 5 CRITICAL)** dans les dépendances transitives de `spring-boot-starter-parent 3.3.5` (Jackson, Tomcat embarqué, driver PostgreSQL, Spring Security/Core). Décision (validée par Taha) : **bump vers `spring-boot-starter-parent 3.5.16`** (même ligne majeure 3.x, pas de saut vers la 4.1.0 disponible sur Maven Central — écarté comme trop risqué à ce stade) — vérifié en local avec la même version de Trivy que la CI : 0 vulnérabilité après bump. Ce bump a cassé `/v3/api-docs` (`NoSuchMethodError` sur `ControllerAdviceBean`, incompatibilité connue springdoc 2.6.x / Spring Framework 6.2.x) — détecté en démarrant réellement l'app, pas seulement via `mvn verify` ; corrigé en bumpant `springdoc.version` vers `2.8.17`. `contracts/openapi.json` régénéré (springdoc 2.8.x passe par défaut à OpenAPI 3.1 au lieu de 3.0 — visible dans le champ `openapi` du contrat), `npm run generate:types` + `tsc -b` re-vérifiés côté frontend (aucun changement, l'API n'expose encore que `/api/health`). Les deux jobs CI (`build-test`, `trivy`) sont verts sur `origin/main` des deux repos. |
