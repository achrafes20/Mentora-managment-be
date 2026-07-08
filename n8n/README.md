# n8n — transport only

Rappel (`ai-instructions.md`) : n8n ne fait **que** du transport (webhooks,
cron, SMTP/IMAP). Toute la logique métier (normalisation, dédup, règles
J-3/J-15, etc.) vit dans le backend Spring Boot. Les workflows sont
versionnés en JSON ici ; **les credentials (SMTP, IMAP, API keys) ne sont
jamais exportés ni committés** — chaque dev les recrée localement une fois
(voir « Premier lancement » ci-dessous).

## Tableau workflow ↔ EF ↔ credentials

| Fichier (`n8n/workflows/`)      | Déclencheur                    | EF concernée(s)                          | Credentials requis (à recréer localement) |
|----------------------------------|---------------------------------|-------------------------------------------|--------------------------------------------|
| `ZljnpSfmg1POyS7P.json` — *Webhook vers SMTP (Mailpit)* | Webhook `POST /webhook/notify-email` (body : `to`, `subject`, `message`) | Socle T0.B3 — valide la chaîne export/import et sert de canal e-mail de référence pour toutes les EF qui enverront un e-mail (reset mot de passe EF-AUTH, rejet candidature EF-REC-14, certificats EF-DOC-07/11, etc.) | Credential SMTP nommé `Mailpit (dev)` → host `mailpit`, port `1025`, SSL/TLS désactivé, pas d'auth |

*(Ce tableau grossira à chaque nouveau workflow ajouté en Phase 1+ : IMAP →
ingestion recrutement EF-REC, cron surveillance documents EF-DOC-12→14,
etc. Une ligne par fichier de `n8n/workflows/`.)*

## Premier lancement (à faire une fois par dev, par volume Docker)

n8n stocke ses workflows/credentials dans un volume Docker persistant
(`rh_n8n_data`) — vide au premier démarrage.

1. `docker compose up -d n8n mailpit` (ou `make up-d`).
2. Ouvrir <http://localhost:5678> : n8n présente l'assistant de création du
   compte propriétaire (email/mot de passe — local, aucun lien avec les
   comptes `utilisateurs` du backend). À faire une seule fois par volume.
3. Recréer le credential SMTP `Mailpit (dev)` (Credentials → New →
   *SMTP*) :
   - Host : `mailpit`
   - Port : `1025`
   - SSL/TLS : désactivé
   - Disable STARTTLS : activé
   - User / Password : vides
   - Tester la connexion (bouton intégré) avant de sauvegarder.
4. `make n8n-import` pour charger les workflows versionnés dans
   `n8n/workflows/`.
5. Chaque workflow importé arrive **désactivé** (limitation n8n en mode
   déploiement standard — l'activation programmatique
   `import:workflow --activeState=fromJson` n'est supportée qu'en mode
   queue/multi-main). Activer manuellement le toggle dans l'UI n8n pour
   chaque workflow qui doit tourner.

## Procédure export / import

- **Après avoir modifié un workflow dans l'UI n8n** : `make n8n-export`
  (exécute `n8n export:workflow --all --separate --pretty
  --output=/n8n/workflows/`, un fichier JSON par workflow, à committer).
- **Après avoir cloné le repo / reset le volume n8n** : `make n8n-import`
  (exécute `n8n import:workflow --separate --input=/n8n/workflows/`), puis
  recréer les credentials manquants (étape 3 ci-dessus) et activer les
  workflows dans l'UI.
- Les deux cibles nécessitent que le service `n8n` tourne
  (`make up-d` d'abord).
- **Sous Git Bash sur Windows** : préfixer la commande avec
  `MSYS_NO_PATHCONV=1` si `make n8n-export`/`n8n-import` échoue avec une
  erreur de chemin du style `C:/Program Files/Git/n8n/workflows/` — Git
  Bash réécrit sinon `/n8n/workflows/` (chemin *dans le conteneur*) en
  chemin Windows avant de l'envoyer à `docker compose exec`.

## Test rapide du workflow de référence

```bash
curl -X POST http://localhost:5678/webhook/notify-email \
  -H "Content-Type: application/json" \
  -d '{"to":"rh@hbdev.ma","subject":"Test","message":"Bonjour"}'
```

Puis vérifier la réception dans l'UI Mailpit (<http://localhost:8025>) ou
via son API (`GET http://localhost:8025/api/v1/messages`).
