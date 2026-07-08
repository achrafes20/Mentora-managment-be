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
   les valeurs locales.
3. `docker compose up` — démarre le backend, PostgreSQL, et le frontend
   (service référencé depuis `../Mentora-managment-fe`).

