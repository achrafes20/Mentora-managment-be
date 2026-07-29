# Dossier technique de déploiement — Gestion RH

---

## 1. Composants

| Composant | Technologie | Rôle |
|---|---|---|
| Backend | Spring Boot, Java 21 | API REST, migrations Flyway au démarrage, jobs planifiés. |
| Frontend | React + Vite, servi par nginx | Site statique. |
| Base de données | PostgreSQL 14+ | Persistance unique. |
| n8n | `n8nio/n8n:2.29.8` | Transport pur : IMAP → ingestion CV, envoi SMTP. Son indisponibilité n'affecte ni le frontend, ni l'API, ni la base — seuls l'ingestion des CV et les envois d'e-mails s'interrompent. |

Deux dépôts, deux pipelines, deux images : `Mentora-managment-be` (pivot — backend, compose, migrations, workflows n8n) et `Mentora-managment-fe` (frontend seul).

---

## 2. Images Docker

| Composant | Image | Base build | Base runtime | Utilisateur |
|---|---|---|---|---|
| Backend | `mentora-backend` | `eclipse-temurin:21-jdk-alpine` | `eclipse-temurin:21-jre-alpine` | non-root (`rh`) |
| Frontend | `mentora-frontend` | `node:22-alpine` | `nginxinc/nginx-unprivileged:alpine` | non-root (`101`) |

**Aucune image n'est publiée sur un registre à ce jour** — `release.yml` construit et scanne (Trivy) les deux images mais ne les pousse pas encore. **C'est le seul vrai blocage pour écrire vos manifests.** Cible convenue : GHCR, tag `sha-<court>`. 

---

## 3. Ports

- **Backend** : écoute sur `8080`.
- **Frontend** : écoute sur `8081` — pas `80` : `nginx-unprivileged` tourne en non-root et ne peut pas se lier à un port privilégié (< 1024).


---

## 4. Healthchecks

| Sonde | Endpoint | Contenu |
|---|---|---|
| Liveness | `GET /actuator/health/liveness` | Process seul, **sans** la base — une panne DB ne doit pas redémarrer le pod. |
| Readiness | `GET /actuator/health/readiness` | Process **+ connectivité PostgreSQL**. |

Chemins publics, aucun autre endpoint Actuator exposé.

```yaml
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
  initialDelaySeconds: 15
  periodSeconds: 5
  failureThreshold: 3
startupProbe:                       # requis : migrations Flyway au premier démarrage
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  failureThreshold: 30
  periodSeconds: 10
```

Frontend : `GET /` → `200` dès que nginx est prêt, suffit pour les deux probes.

---

## 5. Variables d'environnement

### Backend

`[SECRET]` → objet `Secret` Kubernetes, jamais un ConfigMap.

| Variable | Req. | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Non | Laisser vide hors dev (pas de profil `prod`). |
| `SERVER_PORT` | Non | Défaut `8080`. |
| `SPRING_DATASOURCE_URL` | **Oui** | URL JDBC, ex. `jdbc:postgresql://rh-postgres:5432/rh`. |
| `SPRING_DATASOURCE_USERNAME` | **Oui** | Droits DML **+ DDL** (Flyway crée le schéma). |
| `SPRING_DATASOURCE_PASSWORD` | **[SECRET]** | |
| `JWT_SECRET` | **[SECRET]** | Min. 64 caractères, propre à chaque environnement. |
| `JWT_EXPIRATION_MS` | Non | Défaut `86400000` (24h). |
| `APP_BASE_URL` | **Oui** | URL publique du frontend. Sert aux liens de réinitialisation de mot de passe. |
| `API_BASE_URL` | **Oui** | URL publique de l'API. Sert aux liens de téléchargement de documents. En prod, identique à `APP_BASE_URL`. |
| `CORS_ALLOWED_ORIGINS` | **Oui** | Origines autorisées, séparées par des virgules. |
| `FILE_STORAGE_PATH` | **Oui** | Doit pointer sur un volume persistant (§7). Défaut `./data/uploads`. |
| `FILE_MAX_SIZE_MB` | Non | Défaut `10`. |
| `INTERNAL_WEBHOOK_SECRET` | **[SECRET]** | Endpoints appelés par n8n. Vide = ces endpoints refusent tout appel. |
| `OPENROUTER_API_KEY` | [SECRET] | Absente = analyse IA désactivée proprement (l'app démarre normalement). |
| `OPENROUTER_MODEL` | Non | Défaut `openai/gpt-oss-20b:free`. |
| `MATTERMOST_BASE_URL` | Non | Vide = notifications in-app uniquement. |
| `MATTERMOST_BOT_TOKEN` | [SECRET] | |
| `MATTERMOST_BOT_USER_ID` | Non | |
| `MATTERMOST_LOOKUP_USER_BY_EMAIL` | Non | Défaut `true`. |
| `MATTERMOST_CONNECT_TIMEOUT_MS` | Non | Défaut `3000`. |
| `MATTERMOST_READ_TIMEOUT_MS` | Non | Défaut `5000`. |
| `NOTIFICATIONS_ARCHIVAGE_CRON` | Non | Défaut `0 15 2 * * *`. |
| `NOTIFICATIONS_RETENTION_JOURS` | Non | Défaut `90`. |
| `DELEGATIONS_EXPIRATION_CRON` | Non | Défaut `0 30 2 * * *`. |
| `DOCUMENTS_SURVEILLANCE_CRON` | Non | Défaut `0 0 3 * * *`. Voir §8 (ne pas scaler avant d'avoir tranché). |

### Frontend

Site statique compilé : config figée au build, sauf la variable suivante lue par nginx au démarrage.

| Variable | Req. | Description |
|---|---|---|
| `BACKEND_ORIGIN` | **Oui** | Origine du backend vue depuis le pod frontend, ex. `http://rh-backend:8080`. |



### PostgreSQL (référence)

| Variable | Req. | Description |
|---|---|---|
| `POSTGRES_DB` | Oui | |
| `POSTGRES_USER` | Oui | = `SPRING_DATASOURCE_USERNAME`. |
| `POSTGRES_PASSWORD` | [SECRET] | = `SPRING_DATASOURCE_PASSWORD`. |

### n8n (référence)

| Variable | Req. | Description |
|---|---|---|
| `N8N_ENCRYPTION_KEY` | **[SECRET]** | Chiffre les credentials n8n. Propre à chaque environnement — jamais partagée. |
| `N8N_HOST` | Oui | |
| `WEBHOOK_URL` | Oui | |
| `GENERIC_TIMEZONE` | Oui | `Africa/Casablanca`. |

L'UI n8n doit être protégée en production (auth native n8n, ou hors Ingress) — non configuré dans notre compose de dev.

---

## 6. Base de données

- PostgreSQL 14+ requis (types ENUM, extensions `pg_trgm` + `pgcrypto`).
- L'utilisateur applicatif doit pouvoir créer une extension au premier déploiement, ou `pgcrypto`/`pg_trgm` doivent être pré-installées par un superutilisateur.
- Flyway est embarqué dans le backend, s'exécute automatiquement au démarrage, pose un verrou (plusieurs pods au démarrage = sûr). `baseline-on-migrate: false` : une base non vide sans table `flyway_schema_history` fait échouer le démarrage — la base cible doit être vierge ou déjà gérée par Flyway.
- Alternative : `SPRING_FLYWAY_ENABLED=false` + Job dédié avant rollout.

⚠️ **Compte Admin de seed public** : `admin@hbdev.ma` / `admin123`. À changer immédiatement après le premier déploiement (depuis l'écran « Mon compte », pas besoin d'accès SQL).

Jours fériés et horaires de référence ne sont pas préremplis — saisie manuelle par l'Admin RH via l'interface après déploiement.

---

## 7. Volumes

| Composant | Volume | Dimensionnement |
|---|---|---|
| Backend | **PersistentVolumeClaim requis** | Fichiers uploadés sous `FILE_STORAGE_PATH`. 5–10 GiB au départ. |
| Frontend | Aucun | |
| PostgreSQL | PersistentVolumeClaim | 20 GiB au départ. |
| n8n | PersistentVolumeClaim (petit) | ~1 GiB. |

⚠️ Les fichiers uploadés sont sur **disque**, pas en base (la table `fichiers` ne garde que le chemin). Sans volume persistant, ils disparaissent à chaque redémarrage de pod. **Si plusieurs réplicas backend (§8), ce volume doit être `ReadWriteMany`** — un fichier téléversé par un pod doit être lisible par les autres.

Le backend écrit aussi des logs sous `/app/logs`. Avec `readOnlyRootFilesystem: true`, montez un `emptyDir` sur `/app/logs` et `/tmp`.

---

## 8. Scaling — question ouverte

Le backend exécute 4 tâches planifiées la nuit (anomalies de pointage 01h00, archivage notifications 02h15, expiration délégations 02h30, surveillance fins de contrat 03h00 — envoi d'e-mails). Elles sont internes à chaque pod : **avec plusieurs réplicas, chaque tâche s'exécute une fois par pod** (ex. 3 pods = le même e-mail envoyé 3 fois). Aucune coordination entre pods aujourd'hui.

À 1 réplica, aucun problème — c'est le cas nominal.

**Comment voulez-vous traiter ce point avant qu'on scale ?** Deux pistes possibles de notre côté, à voir ensemble selon ce qui vous convient :

- On ajoute un verrou partagé dans l'application (un seul pod exécute chaque tâche, peu importe le nombre de réplicas).
- On désactive l'ordonnancement interne et vous le pilotez depuis des CronJobs Kubernetes.

Le reste scale sans souci : Flyway (verrou géré), JWT stateless (pas de sticky sessions requis).

---

## 9. n8n

3 workflows versionnés dans `n8n/workflows/`, importés via `n8n import:workflow --separate --input=n8n/workflows/` :

| Fichier | Rôle |
|---|---|
| `T3B1RecrutementImapIngest001.json` | IMAP → `POST /api/recruitment/ingest`. |
| `T3B1RecrutementArchivageCron001.json` | Cron quotidien 03h00 → `POST /api/recruitment/candidatures/archiver-expirees`. |
| `ZljnpSfmg1POyS7P.json` | Webhook `/webhook/notify-email` → envoi SMTP (seul chemin de sortie des e-mails). |

Les deux premiers sont protégés par `INTERNAL_WEBHOOK_SECRET` (en-tête `X-Internal-Webhook-Secret`).

- Les credentials (IMAP, SMTP) ne sont **pas** dans l'export — à recréer manuellement dans l'UI n8n au premier déploiement.
- Les workflows importés sont **désactivés** — à activer manuellement (pas de réactivation auto à l'import).


---

## 10. Secrets

| Env. | Mécanisme |
|---|---|
| Dev | `.env` local, gitignoré |
| CI | GitHub Actions secrets |
| **Prod** | **À définir de votre côté** (Secrets K8s natifs, External Secrets, Sealed Secrets…) |

Secrets à provisionner : `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `INTERNAL_WEBHOOK_SECRET`, `OPENROUTER_API_KEY`, `MATTERMOST_BOT_TOKEN`, `POSTGRES_PASSWORD`, `N8N_ENCRYPTION_KEY`.

- `JWT_SECRET` et `N8N_ENCRYPTION_KEY` : jamais partagés entre environnements.
- Les valeurs par défaut d'`application.yml` sont publiques (dans Git) — tout `[SECRET]` non surchargé en prod tourne avec une valeur connue de quiconque a accès au dépôt.

---

## 11. Points ouverts

1. **Aucune image publiée** (§2) — bloquant pour vos manifests.
2. **Mot de passe Admin de seed public** (§6) — à changer au premier déploiement.
3. **Scaling backend** (§8) — en attente de votre réponse.
4. Pas de SAST (Trivy couvre les CVE de dépendances, pas les patterns applicatifs).

Décisions qui vous appartiennent : mécanisme de secrets prod, exécution des migrations (démarrage vs Job), protection de l'UI n8n, classe de stockage du volume backend, TLS/Ingress.


