# contracts/

Contrat d'API généré, pas écrit à la main.

- `openapi.json` : exporté par springdoc-openapi (`make openapi-export`,
  branché en T0.A2). Régénéré à chaque PR backend qui touche un endpoint.
- Consommé par `Mentora-managment-fe` (`npm run generate:types` →
  `src/types/api.ts`).

Ce dossier est vide tant que T0.A2 n'a pas branché springdoc-openapi.
