# Mentora-managment-be

Backend Spring Boot du système de gestion RH pour HB Développement — repo pivot
(docker-compose, migrations Flyway, docs projet, workflows n8n).

## Démarrage rapide

Ce repo est le pivot d'un workspace à **deux repos clonés côte à côte** : il
référence son repo frère frontend dans son docker-compose et ne fonctionne
pas isolément.

```
workspace/
├── Mentora-managment-be/   <- ce repo
└── Mentora-managment-fe/
```

1. Cloner les deux repos côte à côte dans un même dossier `workspace/`.
2. Depuis `Mentora-managment-be/` : `cp .env.example .env`, puis renseigner
   les valeurs locales (les valeurs par défaut suffisent pour un premier
   lancement).
3. `make install-hooks` — installe le hook pre-commit (Spotless +
   Checkstyle). **Étape manuelle à refaire à chaque clone** 
4. `docker compose up` (ou `make up`) — démarre les 5 services : `db`
   (PostgreSQL 14, migrations Flyway + données de référence appliquées
   automatiquement au démarrage du backend), `backend` (Spring Boot),
   `frontend` (Vite, service référencé depuis `../Mentora-managment-fe`),
   `mailpit` (capture des e-mails sortants) et `n8n` (workflows —
   transport only, voir `n8n/README.md`).

| Service    | URL locale                        | Notes |
|------------|------------------------------------|-------|
| `backend`  | http://localhost:8080              | `/actuator/health`, `/swagger-ui.html`, `/v3/api-docs` |
| `frontend` | http://localhost:5173              | Vite dev server, HMR actif |
| `db`       | http://localhost:5432                     | `rh_dev` / `rh_dev` par défaut |
| `mailpit`  | http://localhost:8025              | UI web — tous les e-mails envoyés via n8n atterrissent ici |
| `n8n`      | http://localhost:5678              | **Premier lancement uniquement** : nécessite la création d'un compte propriétaire local puis l'import des workflows — voir `n8n/README.md` |

`backend`, `frontend`, `db` et `mailpit` sont utilisables immédiatement
après `docker compose up`. `n8n` nécessite un court bootstrap manuel une
seule fois par volume Docker (`rh_n8n_data`) — détaillé dans
`n8n/README.md` (§ Premier lancement).

## Carte du dépôt

Feature-based, jamais organisé par couche technique (`ai-instructions.md`
règle 1) :

```
src/main/java/ma/hbdev/rh/
├── auth/            Login JWT, comptes, délégation d'approbation
├── employee/        Employés, départements, import Excel/CSV
├── attendance/      Pointage QR, télétravail, anomalies, horaires
├── recruitment/     Offres, candidatures, analyse IA des CV, entretiens
├── administrative/  Demandes (congés, bons de sortie), ledger de congés
├── document/        Certificats, surveillance J-3/J-15 fin de contrat
├── config/          Identité entreprise, consultation du journal d'audit
├── notification/    Centre de notifications in-app + Mattermost
└── shared/          Infra transverse — jamais de dépendance vers une feature métier (ai-instructions.md règle 4/6) :
    ├── ai/            CvAnalysisProvider (vendor IA quarantiné)
    ├── audit/         journal_audit (append-only, RULES SQL)
    ├── event/         contrat EvenementMetier (audit + notification)
    ├── export/        rendu Excel/PDF générique
    ├── file/          stockage fichiers (upload CV, photos...)
    ├── mail/          client SMTP (webhook n8n)
    ├── mattermost/    client webhook Mattermost (notifications privées)
    ├── security/      RBAC, JWT, CurrentUser, InternalWebhookGuard
    └── web/           format de réponse API, pagination, erreurs
```

Autres dossiers notables :

- `src/main/resources/db/migration/` — migrations Flyway (`V<n>__*.sql`),
  schéma + données de référence uniquement, jamais de données RH réelles
  (`ai-instructions.md`). Un fichier mergé est immuable.
- `contracts/openapi.json` — spec OpenAPI exportée, consommée par le
  frontend pour générer ses types (`npm run generate:types`). Régénérée à
  chaque PR qui change un endpoint (`make openapi-export`).
- `n8n/workflows/` — workflows n8n versionnés en JSON (transport only :
  IMAP, cron, SMTP — jamais de logique métier). Voir `n8n/README.md`.
- `docs/` — `ai-instructions.md` (règles stables du projet, à lire en
  premier), `avancement-projet.md` (plan séquencé + statut vivant + journal
  de session détaillé — la meilleure source pour comprendre *pourquoi* une
  décision a été prise), `01-requirements.md` (exigences EF-*/NFR-*).
- `scripts/` — hook pre-commit (Spotless + Checkstyle) et
  `seed-dev-data.sh` (données de dev via l'API réelle, jamais par SQL).

## Tâches courantes

| Tâche | Commande |
|---|---|
| Ajouter une migration | Créer `src/main/resources/db/migration/V<n+1>__description.sql` (jamais modifier un fichier déjà mergé) |
| Lancer les tests | `make test` (= `./mvnw verify`, Testcontainers — **Docker doit tourner**) |
| Vérifier le style | `make lint` (Spotless + Checkstyle) |
| Corriger le formatage | `make format` |
| Régénérer le contrat API | `make openapi-export` (démarre temporairement le backend, télécharge `/v3/api-docs`, l'arrête) |
| Réinitialiser la base de dev | `make reset-db` puis `make seed-dev` (volume Postgres frais, n8n conservé) |
| Exporter/importer les workflows n8n | `make n8n-export` / `make n8n-import` (n8n doit tourner) |
| Voir toutes les cibles | `make help` |

## CI/CD en 5 lignes

- **`ci.yml`** (push/PR sur `main`) : job `build-test` — Spotless, Checkstyle,
  `mvn verify` (tests + Testcontainers) ; job `trivy` — scan de
  vulnérabilités des dépendances (`fs`, CRITICAL/HIGH, échoue le build).
- **`release.yml`** (push sur `main`) : build l'image Docker multi-stage
  (stage `prod`, non-root) et la scanne avec Trivy (`image`,
  CRITICAL/HIGH). **Ne pousse pas encore vers un registre** — cible
  retenue GHCR, pas encore implémentée (voir T1.A2 dans
  `avancement-projet.md`).
- Pas de CodeQL/SAST — licence GitHub Advanced Security non budgétée sur
  repo privé ; à revoir en T6.4 (durcissement).

## Dépannage

Problèmes réels rencontrés en développant ce projet — voir
`avancement-projet.md` (Suivi de session) pour le détail complet de
chacun.

- **Les tests échouent avec `IllegalStateException: Previous attempts to
  find a Docker environment failed`.** Docker Desktop n'est pas démarré
  (ou vient de redémarrer) — Testcontainers a besoin d'un démon Docker
  actif. Démarrer Docker, relancer les tests.
- **`docker compose up` échoue après avoir changé de branche, avec une
  erreur de checksum Flyway.** Un volume Postgres local garde l'historique
  de migrations d'une autre branche. `make down-v` (ou
  `docker compose down -v`) puis relancer — destructif pour les données de
  dev locales uniquement.
- **`docker compose up --build` échoue à résoudre
  `registry-1.docker.io`.** Coupure réseau temporaire, pas un problème de
  code — relancer une fois la connexion revenue.
- **`LazyInitializationException` (500) sur une relation `LAZY` d'une
  entité JPA.** Symptôme récurrent : une entité chargée via un simple
  `findById()` puis mappée en DTO dans le contrôleur, hors de la
  transaction du service. Corrigé en ajoutant une méthode de repository
  dédiée avec `LEFT JOIN FETCH` sur la relation concernée (voir
  `EmployeRepository`/`CandidatureRepository` pour des exemples), jamais en
  élargissant la transaction jusqu'au contrôleur.
- **Un test d'intégration échoue avec une contrainte FK violée, alors que
  le `@BeforeEach` fait un `TRUNCATE ... utilisateurs CASCADE`.** Ce
  `CASCADE` emporte silencieusement toute table de référence qui a une FK
  vers `utilisateurs` (ex. `politique_conges`, `politique_anomalies`),
  pas seulement les données de test. Nettoyer `utilisateurs` par un
  `DELETE` simple (après avoir mis à `NULL` les colonnes `modifie_par`
  pendantes) plutôt qu'un `TRUNCATE ... CASCADE`, et réinitialiser les
  données de référence dans le `@BeforeEach`.
- **Une requête anonyme sur un endpoint protégé renvoie 403, pas 401.**
  Comportement normal, pas un bug : `SecurityConfig` ne déclare pas
  d'`AuthenticationEntryPoint` personnalisé, Spring Security retombe sur
  `Http403ForbiddenEntryPoint` par défaut.
- **`rs.getObject(colonne, Instant.class)` lève une erreur de conversion
  sur une colonne `timestamptz`.** pgjdbc ne supporte pas cette conversion
  directe — lire en `OffsetDateTime` puis appeler `.toInstant()`.
- **L'analyse IA d'une candidature prend 20 à 60 secondes, ou finit en
  "échec".** Normal pour le modèle gratuit OpenRouter (`openai/gpt-oss-20b:free`)
  — il génère une grande quantité de tokens de raisonnement caché avant de
  répondre (mesuré : ~22s pour un prompt court, jusqu'à ~60s sur un CV
  long). Le timeout de lecture est réglé à 60s en conséquence
  (`CvAnalysisConfig`) ; en dessous, ce n'est pas un vrai échec d'analyse.

