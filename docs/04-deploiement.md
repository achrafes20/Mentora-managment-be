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

**Les deux images sont publiées sur GHCR** — `ghcr.io/mentora-ma/mentora-management-be` et `ghcr.io/mentora-ma/mentora-management-fe`, taguées `latest` et `sha-<court>`. Publication ajoutée aux deux `release.yml`, le push est gated par le scan Trivy (`exit-code: 1` sur `CRITICAL,HIGH` non corrigeable — le job échoue et s'arrête avant d'atteindre le push). Vos manifests peuvent référencer ces images dès maintenant.

---

## 3. Ports

- **Backend** : écoute sur `8080`.
- **Frontend** : écoute sur `8081` — pas `80` : `nginx-unprivileged` tourne en non-root et ne peut pas se lier à un port privilégié.


---

## 4. Healthchecks

| Sonde | Endpoint | Contenu |
|---|---|---|
| Liveness | `GET /actuator/health/liveness` | Process seul, **sans** la base — une panne DB ne doit pas redémarrer le pod. |
| Readiness | `GET /actuator/health/readiness` | Process **+ connectivité PostgreSQL**. |

Chemins publics, aucun autre endpoint Actuator exposé.

Frontend : `GET /` → `200` dès que nginx est prêt, suffit pour les deux probes.

---

## 5. Variables d'environnement

Comment les secrets sont provisionnés selon l'environnement :

| Environnement | Mécanisme |
|---|---|
| Dev | `.env` local, gitignoré |
| CI | GitHub Actions secrets |
| **Prod** | **À définir de votre côté** (Secrets K8s natifs, External Secrets, Sealed Secrets…) |

`JWT_SECRET` et `N8N_ENCRYPTION_KEY` : jamais partagés entre environnements. Les valeurs par défaut d'`application.yml` sont publiques (dans Git) — tout `[SECRET]` non surchargé en prod tourne avec une valeur connue de quiconque a accès au dépôt.

`[SECRET]` ci-dessous → objet `Secret` Kubernetes, jamais un ConfigMap.

### Backend

| Variable | Req. | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Non | Laisser vide hors dev (pas de profil `prod`). |
| `SERVER_PORT` | Non | Défaut `8080`. |
| `SPRING_DATASOURCE_URL` | **Oui** | URL JDBC, ex. `jdbc:postgresql://rh-postgres:5432/rh`. |
| `SPRING_DATASOURCE_USERNAME` | **Oui** | Droits DML **+ DDL** (Flyway crée le schéma). |
| `SPRING_DATASOURCE_PASSWORD` | **[SECRET]** | |
| `JWT_SECRET` | **[SECRET]** | Min. 64 caractères, propre à chaque environnement. |
| `JWT_EXPIRATION_MS` | Non | Défaut `86400000` (24h). |
| `APP_BASE_URL` | **Oui** | URL publique du frontend. Sert aux liens de réinitialisation de mot de passe, au lien de révocation d'appareil personnel envoyé dans l'e-mail de code de pointage mobile (EF-ATT-19), et à la valeur encodée dans le QR badge de la fiche employé (lien direct vers la fiche, cf. EF-ATT-01). |
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
| `DOCUMENTS_SURVEILLANCE_CRON` | Non | Défaut `0 0 3 * * *`. |
| `RECRUITMENT_ARCHIVAGE_CRON` | Non | Défaut `0 30 3 * * *`. |
| `N8N_BASE_URL` | **Oui** | URL de n8n vue depuis le backend (réseau interne), ex. `http://rh-n8n:5678`. Sert à tous les envois sortants qui passent par le webhook SMTP de n8n : réinitialisation de mot de passe, certificats/documents RH, rejet de candidature, code d'activation et lien de révocation du pointage mobile. |

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

| Composant | Volume |
|---|---|
| Backend | **PersistentVolumeClaim requis** |
| Frontend | Aucun |
| PostgreSQL | PersistentVolumeClaim |
| n8n | PersistentVolumeClaim (petit) |

⚠️ Les fichiers uploadés sont sur **disque**, pas en base (la table `fichiers` ne garde que le chemin). Sans volume persistant, ils disparaissent à chaque redémarrage de pod. **Si plusieurs réplicas backend (§8), ce volume doit être `ReadWriteMany`** — un fichier téléversé par un pod doit être lisible par les autres.

Le backend écrit aussi des logs sous `/app/logs`. Avec `readOnlyRootFilesystem: true`, montez un `emptyDir` sur `/app/logs` et `/tmp`.

---

## 8. Scaling — question ouverte

Le backend exécute **5** tâches planifiées la nuit.

Elles sont internes à chaque pod : **avec plusieurs réplicas, chaque tâche s'exécute une fois par pod** (ex. 3 pods = le même e-mail envoyé 3 fois). Aucune coordination entre pods aujourd'hui.

À 1 réplica, aucun problème — c'est le cas nominal.

**Comment voulez-vous traiter ce point avant qu'on scale ?** Deux pistes possibles de notre côté, à voir ensemble selon ce qui vous convient :

- On ajoute un verrou partagé dans l'application (un seul pod exécute chaque tâche, peu importe le nombre de réplicas).
- On désactive l'ordonnancement interne et vous le pilotez depuis des CronJobs Kubernetes.

Le reste scale sans souci : Flyway (verrou géré), JWT stateless (pas de sticky sessions requis).

---

## 9. n8n

2 workflows versionnés dans `n8n/workflows/`, importés via `n8n import:workflow --separate --input=n8n/workflows/` — transport uniquement (webhook/IMAP) :

| Fichier | Rôle |
|---|---|
| `T3B1RecrutementImapIngest001.json` | IMAP → `POST /api/recruitment/ingest`. Protégé par `INTERNAL_WEBHOOK_SECRET` (en-tête `X-Internal-Webhook-Secret`). |
| `ZljnpSfmg1POyS7P.json` | Webhook `/webhook/notify-email` → envoi SMTP (seul chemin de sortie des e-mails). |

*(voir `n8n/README.md`)*
- Les credentials (IMAP, SMTP) ne sont **pas** dans l'export — à recréer manuellement dans l'UI n8n au premier déploiement.
- Les workflows importés sont **désactivés** — à activer manuellement (pas de réactivation auto à l'import).


---

## 10. Points de vigilance pour l'équipe DevOps

1. Le mot de passe Admin de seed (`admin123`, §6) est public dans ce dépôt — à changer dès le premier déploiement.
2. Les tâches planifiées du backend (§8) ne sont pas coordonnées entre plusieurs réplicas — un point à surveiller si vous scalez au-delà d'un seul pod.
3. Les secrets de production (§5) ne sont pas gérés de notre côté — à mettre en place avant le déploiement.
4. L'interface n8n (§9) n'est pas protégée dans notre configuration de dev — à sécuriser en production.
5. Le TLS/Ingress reste à configurer selon vos standards habituels.
6. Le pointage mobile (§5, `APP_BASE_URL`) dépend de n8n/SMTP dès la création d'un employé : le code d'activation par téléphone n'est envoyé que par e-mail. Si n8n ou le SMTP est indisponible à ce moment-là, l'employé ne peut pas s'appairer tant qu'un Admin n'a pas régénéré le code manuellement depuis l'écran Kiosque.

