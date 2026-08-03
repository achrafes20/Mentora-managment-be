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
| `T3B1RecrutementImapIngest001.json` — *IMAP vers ingestion recrutement* | Déclencheur IMAP (nouvel e-mail reçu) → `POST /api/recruitment/ingest` | EF-REC-02/03 (T3.B1) | Credential IMAP nommé `IMAP Recrutement (dev)` (host/port/user/password de la boîte de test — voir « Boîte mail de recrutement » ci-dessous) + credential Header Auth nommé `Recrutement Webhook Secret (dev)` (en-tête `X-Internal-Webhook-Secret`, valeur = `INTERNAL_WEBHOOK_SECRET` du `.env` backend) |

*(Ce tableau grossira à chaque nouveau workflow de transport ajouté —
webhook/IMAP uniquement, plus aucun cron : voir note ci-dessous. Une ligne
par fichier de `n8n/workflows/`.)*

> **Cron archivage candidatures retiré (2026-08-03).** L'archivage EF-REC-12
> (candidatures "En attente" au-delà de la fenêtre de rétention) tournait via
> `T3B1RecrutementArchivageCron001.json` (cron quotidien 3h → `POST
> /api/recruitment/candidatures/archiver-expirees`, protégé par
> `InternalWebhookGuard`). C'était le seul des 5 traitements planifiés du
> backend à encore dépendre de n8n — les 4 autres (`AnomalieService`,
> `DelegationService`, `NotificationService`,
> `SurveillancePlanifieeService`) sont déjà des `@Scheduled` Spring. Migré au
> même mécanisme : `CandidatureService#archivageQuotidien` (cron
> `app.recruitment.archivage-cron` / `RECRUITMENT_ARCHIVAGE_CRON`, défaut
> `0 0 3 * * *`, zone `Africa/Casablanca`). Le déclenchement manuel reste
> disponible via `POST /api/candidatures/archiver-expirees`, désormais en
> RBAC Admin normal (session JWT) et non plus via secret partagé. n8n ne
> porte donc plus aucun cron, uniquement du transport webhook/IMAP —
> `ai-instructions.md` règle 7 mise à jour en conséquence. Sur une instance
> n8n existante : désactiver et supprimer manuellement ce workflow dans l'UI
> (le fichier JSON a été retiré de `n8n/workflows/`, `make n8n-import` ne le
> recréera pas, mais un import précédent reste actif tant qu'il n'est pas
> désactivé à la main).

### Boîte mail de recrutement (dev)

Contrairement au canal SMTP (Mailpit, partagé par tout le monde), l'ingestion
IMAP (EF-REC-02) a besoin d'une vraie boîte mail à surveiller — Mailpit ne
sert pas de serveur IMAP. Pour le développement local, Taha utilise une
boîte mail personnelle (jamais commitée) : les identifiants IMAP réels
vivent uniquement dans le `.env` local (gitignored) de chaque dev,
`.env.example` ne documente que les clés (`RECRUITMENT_IMAP_HOST`,
`RECRUITMENT_IMAP_PORT`, `RECRUITMENT_IMAP_USER`,
`RECRUITMENT_IMAP_PASSWORD`) avec des valeurs factices. Les vraies valeurs
sont partagées Taha↔Achraf hors dépôt (même principe que
`N8N_ENCRYPTION_KEY`). À la mise en production, cette boîte est remplacée
par la vraie adresse recrutement de HB Développement — seul le credential
IMAP change, aucun code.

Le credential `X-Internal-Webhook-Secret` (Header Auth) protège aussi
l'endpoint ci-dessus contre un appel externe non autorisé — ce n'est ni un
endpoint utilisateur (pas de session JWT), ni ouvert sans contrôle (cf.
`InternalWebhookGuard` côté backend). Sa valeur doit être identique à
`INTERNAL_WEBHOOK_SECRET` dans le `.env` backend.

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
6. **Vérifier que chaque nœud pointe bien vers le credential recréé, pas
   seulement qu'il existe.** Recréer un credential (étape 3) lui donne un
   nouvel identifiant interne — les nœuds d'un workflow importé référencent
   l'ancien identifiant (celui du JSON exporté) et ne se relient **pas**
   automatiquement au nouveau credential du même nom. Symptôme rencontré en
   pratique (2026-07-17) : le webhook répond 200 immédiatement
   (`responseMode: onReceived` déclenche la réponse dès la réception, avant
   l'exécution du reste du workflow), donnant l'illusion d'un envoi réussi
   côté backend/appelant, alors que le nœud "Send Email" échoue en silence
   avec `Credential with ID "..." does not exist for type "smtp"` (visible
   uniquement dans les logs du conteneur `n8n`, jamais dans la réponse HTTP).
   Ouvrir chaque nœud utilisant un credential et le réassigner explicitement
   dans le menu déroulant si besoin.

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
