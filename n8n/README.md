# n8n — transport only

Rappel : n8n ne fait **que** du transport (webhooks,
SMTP/IMAP). Toute la logique métier (normalisation, dédup, règles J-3/J-15,
etc.) vit dans le backend Spring Boot. Les workflows sont versionnés en JSON
ici ; **les credentials (SMTP, IMAP, API keys) ne sont jamais exportés ni
committés** — chaque dev les recrée localement une fois (voir « Premier
lancement » ci-dessous).

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
4. Recréer les credentials du workflow d'ingestion recrutement
   (Credentials → New) :
   - *IMAP* :
     - Host : valeur de `RECRUITMENT_IMAP_HOST` (`.env` backend)
     - Port : valeur de `RECRUITMENT_IMAP_PORT` (`.env` backend)
     - User : valeur de `RECRUITMENT_IMAP_USER` (`.env` backend)
     - Password : valeur de `RECRUITMENT_IMAP_PASSWORD` (`.env` backend)
     - SSL/TLS : activé, Allow self-signed certificates : désactivé
       (boîte de test actuelle sur `imap.gmail.com:993`, certificat valide —
       à réévaluer si `RECRUITMENT_IMAP_HOST` change pour un fournisseur au
       certificat auto-signé).
     - Tester la connexion (bouton intégré) avant de sauvegarder.
   - *Header Auth* :
     - Nom de l'en-tête : `X-Internal-Webhook-Secret`
     - Valeur : celle de `INTERNAL_WEBHOOK_SECRET` (`.env` backend)
5. `make n8n-import` pour charger les workflows versionnés dans
   `n8n/workflows/`.
6. Chaque workflow importé arrive **désactivé** (limitation n8n en mode
   déploiement standard — l'activation programmatique
   `import:workflow --activeState=fromJson` n'est supportée qu'en mode
   queue/multi-main). Activer manuellement le toggle dans l'UI n8n pour
   chaque workflow qui doit tourner.
7. **Vérifier que chaque nœud pointe bien vers le credential recréé, pas
   seulement qu'il existe.** Recréer un credential (étape 3) lui donne un
   nouvel identifiant interne — les nœuds d'un workflow importé référencent
   l'ancien identifiant (celui du JSON exporté) et ne se relient **pas**
   automatiquement au nouveau credential du même nom. 
   Ouvrir chaque nœud utilisant un credential et le réassigner explicitement
   dans le menu déroulant si besoin.

## Workflows en place

| Workflow | Rôle | EF |
|---|---|---|
| Webhook → SMTP (Mailpit) | Relaie un e-mail (reset mot de passe, rejet de candidature, certificats...) vers Mailpit en dev — canal de référence pour toute EF qui envoie un e-mail | EF-AUTH, EF-REC-14, EF-DOC-07/11, etc. |
| IMAP → ingestion recrutement | Surveille la boîte mail recrutement, transmet chaque e-mail reçu (avec pièce jointe CV) tel quel au backend, qui fait l'analyse | EF-REC-02/03 |


## Procédure export / import

- **Après avoir modifié un workflow dans l'UI n8n** : `make n8n-export`
  (exécute `n8n export:workflow --all --separate --pretty
  --output=/n8n/workflows/`, un fichier JSON par workflow, à committer).
  La cible enchaîne automatiquement `scripts/sanitize-n8n-export.js` :
  chaque export n8n embarque, dans `shared[].project.name`, le nom + e-mail
  réel du compte propriétaire de l'instance qui a exporté (ex. `Prenom Nom
  <perso@gmail.com>`) — le script le remplace par un placeholder fixe
  (`"HB Developpement (n8n)"`) avant que le fichier n'atterrisse dans
  `n8n/workflows/`. Rien à faire manuellement ; si le message final indique
  `0/N fichier(s)` nettoyés, c'est que les fichiers étaient déjà propres,
  pas un échec.
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

Bash / Git Bash :

```bash
curl -X POST http://localhost:5678/webhook/notify-email \
  -H "Content-Type: application/json" \
  -d '{"to":"rh@hbdev.ma","subject":"Test","message":"Bonjour"}'
```

Puis vérifier la réception dans l'UI Mailpit (<http://localhost:8025>) ou
via son API (`GET http://localhost:8025/api/v1/messages`).
