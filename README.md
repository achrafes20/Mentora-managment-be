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
   Checkstyle). **Étape manuelle à refaire à chaque clone** : contrairement
   au frontend (Husky s'auto-installe via le script `prepare` de
   `npm install`), Maven n'a pas d'équivalent — `.git/hooks/` n'est pas
   versionné par Git, donc `scripts/pre-commit` ne se déclenche jamais tant
   que cette commande n'a pas été lancée.
4. `docker compose up` (ou `make up`) — démarre les 5 services : `db`
   (PostgreSQL 14, migrations Flyway + données de référence appliquées
   automatiquement au démarrage du backend), `backend` (Spring Boot),
   `frontend` (Vite, service référencé depuis `../Mentora-managment-fe`),
   `mailpit` (capture des e-mails sortants) et `n8n` (workflows —
   transport only, voir `n8n/README.md`).

| Service    | URL locale                        | Notes |
|------------|------------------------------------|-------|
| `backend`  | http://localhost:8080              | `/api/health`, `/swagger-ui.html`, `/v3/api-docs` |
| `frontend` | http://localhost:5173              | Vite dev server, HMR actif |
| `db`       | localhost:5432                     | `rh_dev` / `rh_dev` par défaut |
| `mailpit`  | http://localhost:8025              | UI web — tous les e-mails envoyés via n8n atterrissent ici |
| `n8n`      | http://localhost:5678              | **Premier lancement uniquement** : nécessite la création d'un compte propriétaire local puis l'import des workflows — voir `n8n/README.md` |

`backend`, `frontend`, `db` et `mailpit` sont utilisables immédiatement
après `docker compose up`. `n8n` nécessite un court bootstrap manuel une
seule fois par volume Docker (`rh_n8n_data`) — détaillé dans
`n8n/README.md` (§ Premier lancement).

