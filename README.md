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
   Checkstyle). **Étape manuelle à refaire à chaque clone.**
4. `docker compose up` (ou `make up`) — démarre les 6 services
5. **n8n : bootstrap manuel obligatoire, une fois par volume Docker**
   (`rh_n8n_data`) — `backend`, `frontend`, `db`, `mailpit` et `pgadmin`
   sont utilisables immédiatement après l'étape 4, mais `n8n` démarre à vide
   (pas de compte, pas de workflow importé, pas de credential). Procédure
   complète : `n8n/README.md`  Premier lancement.

| Service    | URL locale             | Notes |
|------------|-------------------------|-------|
| `backend`  | http://localhost:8080   | `/actuator/health`, `/swagger-ui.html`, `/v3/api-docs` |
| `frontend` | http://localhost:5173   | Vite dev server, HMR actif |
| `db`       | http://localhost:5432           | `rh_dev` / `rh_dev` par défaut |
| `mailpit`  | http://localhost:8025   | UI web — tous les e-mails envoyés via n8n atterrissent ici |
| `n8n`      | http://localhost:5678   | Nécessite l'étape 5 ci-dessus avant de pouvoir servir un workflow |
| `pgadmin`  | http://localhost:8082   | `admin@hbdev.ma` / `admin` par défaut (dev uniquement) — connexion à `db` déjà préconfigurée |

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
  IMAP, SMTP — jamais de logique métier). Voir `n8n/README.md`.
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
