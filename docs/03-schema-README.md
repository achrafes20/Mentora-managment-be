# Schéma de base de données — Gestion Entreprise RH

Ce document accompagne le fichier `schema_v1.sql` (PostgreSQL 14+, 24 tables).
Il explique **le rôle de chaque table**, **les décisions structurantes de
conception physique**, et **les divergences assumées par rapport au
diagramme de classes** (`ClassDiagram_000.png` et `02-diagrams-README.md`).

Le schéma est destiné à être lu **avec le cahier des charges
(`01-requirements.md`) sous la main** — chaque colonne significative porte
son code d'exigence en commentaire SQL, et cette documentation reprend la
même traçabilité.

> **Addendum télétravail & reprise de données.** Cette version intègre
> deux ajouts au périmètre : le **télétravail hybride** (nouvelle table
> `plannings_teletravail` en Section 5, dépendance de comportement de la
> détection d'anomalies — cf. §2.12) et la **reprise des données Excel
> existantes** de la RH au démarrage, qui **ne passe pas par le schéma**
> (aucune migration Flyway ne porte de donnée métier réelle — cf. §4).
> Ces deux points sont cohérents avec les patterns déjà présents dans le
> schéma et ne modifient aucune table existante.

---

## 1. Organisation en 11 sections

Le fichier SQL est découpé en 11 sections numérotées qui suivent la
structure des modules métier du cahier des charges, avec l'ajout de deux
sections transverses techniques (Fichiers, Journal d'audit) sans
équivalent direct dans les modules fonctionnels.

| Section | Objet | Correspondance module |
|---|---|---|
| 0 | Types énumérés (`CREATE TYPE`) | Transverse |
| 1 | Authentification, RBAC, délégation | EF-AUTH |
| 2 | Gestion générique des fichiers uploadés | NFR-SEC-07 (transverse) |
| 3 | Départements | EF-EMP-10 |
| 4 | Dossier employé (fiche, transferts, documents, cartes) | EF-EMP |
| 5 | Présence (horaires, QR, pointages, anomalies, plannings de télétravail, jours fériés) | EF-ATT + EF-ADM-10 |
| 6 | Recrutement (offres, candidatures, analyses IA, entretiens) | EF-REC |
| 7 | Demandes administratives et registre des mouvements de congés | EF-ADM |
| 8 | Documents RH (envois) et surveillance planifiée | EF-DOC |
| 8bis | Journal des notifications Mattermost envoyées | Cross-Cutting |
| 9 | Centre de notifications in-app | EF-NOTIF (§2.10) |
| 10 | Configuration système (paramètres clé/valeur) | EF-CFG |
| 11 | Journal d'audit (lecture seule au niveau base) | NFR-SEC-03 + EF-CFG-03→06 |

Cette découpe correspond globalement — mais pas exactement — aux 7
packages du diagramme de classes. Les divergences sont documentées au §3.

---

## 2. Décisions structurantes de conception physique

### 2.1 Une table `fichiers` centralise tous les uploads

Le diagramme de classes stockait des chemins de fichiers directement dans
plusieurs classes (`EmployeeCard.digitalFilePath`,
`EmployeeCard.printFilePath`, `StageCertificate.pdfPath`,
`EmployeeDocument.filePath`). Le schéma centralise tous ces uploads dans
une table unique `fichiers`, avec les métadonnées de sécurité NFR-SEC-07
(type MIME, taille, statut antivirus, motif de rejet) portées une seule
fois plutôt que dupliquées.

Chaque table qui référence un fichier utilise désormais une clé étrangère
`fichier_id: UUID` vers `fichiers`, ce qui donne :
- **Un seul point de validation antivirus/MIME**, appliqué à tout upload
  quel qu'en soit l'usage.
- **Une politique de rétention unifiée** : purger un fichier
  physiquement supprime toutes les références en cascade contrôlée.
- **Une réutilisation possible** : un même fichier peut techniquement
  être référencé par plusieurs entités (rarement utile, mais gratuit).

Cette centralisation est une amélioration réelle par rapport au diagramme
de classes, pas une simple divergence stylistique — elle transforme
NFR-SEC-07 d'une règle applicative dispersée en une contrainte
structurelle unique.

### 2.2 Le registre de mouvements pour le solde de congés

`mouvements_conges` implémente le ledger de la §2.2 du README des
diagrammes. `quantite_jours: NUMERIC(4,1)` supporte les demi-journées
(0,5), les journées entières et les ajustements arbitraires. Le solde à
un instant T se calcule par `SUM(quantite_jours) WHERE employe_id = ?
AND date_mouvement <= ?` plus l'accumulation mensuelle depuis
`employes.date_embauche` (1,5 j/mois pour CDI/CDD, 0 pour stagiaires) —
jamais stocké, toujours calculé à la volée.

Le champ `type_mouvement` (`initialisation`, `consommation`, `recredit`,
`ajustement`) permet de tracer l'origine de chaque ligne : import
initial via EF-EMP-07, consommation à l'approbation d'un congé,
recrédit à l'annulation d'un congé approuvé, ou ajustement manuel de
l'Admin (par exemple lors d'une promotion stagiaire → CDI).

### 2.3 Historisation vs mutation

Trois entités du schéma sont **historisées** plutôt que mutées en place :

- **`horaires_reference`** : chaque modification de l'horaire entreprise
  crée une nouvelle ligne avec `date_effet`. Un pointage référence via
  `horaire_reference_id` l'horaire en vigueur *à sa date*, ce qui
  garantit que "cet employé était-il en retard ce jour-là" reste
  reproductible même si l'horaire change plus tard (EF-ATT-07).
- **`employe_transferts`** : chaque transfert de département/manager
  d'un employé crée une nouvelle ligne, avec anciens et nouveaux
  rattachements, date d'effet, et auteur du transfert (EF-EMP-11).
  `employes.departement_id` et `employes.manager_id` reflètent la
  situation courante ; l'historique complet vit dans cette table.
- **`analyses_ia`** avec la chaîne `remplace_analyse_id` : chaque relance
  (`retry()`) crée une nouvelle ligne pointant vers l'ancienne, plutôt
  qu'écraser. `candidatures.analyse_courante_id` pointe vers la
  dernière en date. L'historique des échecs et succès est conservé.

Le pattern est le même : ce qui importe pour l'audit et la reprise
d'historique est stocké en append-only, ce qui importe pour l'affichage
courant est un pointeur ou un champ dénormalisé.

### 2.4 QR code séparé de l'employé, avec dénormalisation contrôlée

`qr_codes` est une table à part entière comme prévu par le diagramme de
classes (§2.4 du README des diagrammes). Un employé peut avoir plusieurs
QR au fil du temps (régénération après perte, blocage suite à abus),
seul un est actif à la fois (contrôle applicatif, non par contrainte
SQL car un `UNIQUE` partiel serait fragile).

`pointages` porte **à la fois** `employe_id` et `qr_code_id`, ce qui est
une **dénormalisation intentionnelle** : `qr_code_id` est
nécessaire pour l'audit ("quel QR a effectivement scanné ce jour-là,
y compris s'il a été bloqué depuis"), tandis que `employe_id` est
dupliqué pour la performance des requêtes d'historique
(EF-ATT-05, qui pagine sur `employe_id + horodatage` en permanence).
Le `pointages.employe_id` est techniquement dérivable de
`pointages.qr_code_id → qr_codes.employe_id`, mais faire cette
jointure à chaque affichage d'historique aurait un coût que la
dénormalisation évite.

### 2.5 Héritage aplati en table unique pour les demandes administratives

Le diagramme de classes modélisait une hiérarchie
`AdministrativeRequest` → `LeaveRequest` / `ExitPermit`. Le schéma
aplatit ces trois classes en une table unique `demandes_administratives`
avec un discriminateur `type_demande` (`conge`, `bon_sortie`,
`document_libre`, `autre`) et des colonnes spécifiques à chaque type
qui coexistent (`granularite`, `heure_depart`, `heure_retour_prevue`,
`fichier_document_libre_id`).

Ce choix est le pattern "single-table inheritance" — défendable pour
2-3 sous-types avec peu de champs spécifiques, moins défendable si le
nombre de sous-types explose. Les CHECK constraints
(`chk_conge_granularite`, `chk_bon_sortie_horaires`) portent la partie
"tel champ obligatoire pour tel type" que l'héritage classique aurait
portée par typage. C'est une contrepartie réelle : un développeur qui
lit `demandes_administratives` seule ne voit pas immédiatement que
`granularite` n'a de sens que pour les congés — il doit lire les CHECK
ou la doc.

L'alternative aurait été trois tables jointes ; elle aurait été plus
pure théoriquement mais aurait complexifié toutes les requêtes
d'historique (EF-ADM-06) sans bénéfice tangible.

### 2.6 Documents unifiés en `envois_documents`

Le diagramme de classes proposait deux classes parallèles
`StageCertificate` et `WorkCertificate` avec la même structure. Le
schéma les unifie dans une table `envois_documents` discriminée par
`type_document` (`certificat_stage`, `certificat_travail`,
`document_libre`, `email_rejet_candidature`).

C'est la divergence de conception la plus visible entre le diagramme et
le schéma. Elle est **assumée** : le diagramme argumentait pour la
séparation sur la base d'un comportement métier différent (surveillance
planifiée pour le stage, déclenchement manuel pour le travail), mais
cette différence de comportement vit **dans l'application, pas dans la
structure des données**. Les tables `notifications_planifiees` et
`envois_documents` restent séparées, ce qui rend visible le
"comportement planifié" à l'endroit qui compte vraiment (§2.7 ci-dessous).

Bénéfice concret de l'unification : la journalisation d'envoi (EF-DOC-06)
et le renvoi manuel (EF-DOC-07/11) sont codés une seule fois, applicables
uniformément aux quatre types de document.

### 2.7 Trois tables de notifications distinctes

Le schéma introduit trois tables pour ce que le diagramme de classes
appelait `Notification`. Chacune a un rôle bien distinct :

- **`notifications_mattermost`** : journal des envois Mattermost
  **ponctuels** (nouvelle candidature en entretien EF-REC-08, décision
  sur une demande EF-ADM-08, création d'un employé EF-EMP-13, période de
  délégation EF-AUTH-15). Une ligne par envoi tenté, avec succès ou
  échec (`echec BOOLEAN`, `erreur TEXT`).
- **`notifications_planifiees`** : file d'attente des notifications
  **planifiées** générées par la surveillance n8n (fin de stage EF-DOC-02,
  fin de CDD EF-DOC-13/14). Ligne créée à l'ouverture de la surveillance,
  mise à jour au fur et à mesure des envois (`premiere_notification_envoyee_le`,
  `relance_envoyee_le`), annulable si la date de fin change (EF-DOC-15) ou
  si l'employé est désactivé (EF-DOC-16).
- **`notifications_in_app`** : canal de repli in-app (EF-NOTIF, §2.10).
  Créée systématiquement en parallèle de chaque envoi Mattermost
  (EF-NOTIF-01), y compris si l'envoi Mattermost échoue (EF-NOTIF-06 —
  le champ `mattermost_reussi` matérialise cette indépendance).

Regrouper les trois dans une table unique aurait forcé des colonnes
inutiles pour deux tiers des lignes (les champs de planification n'ont
pas de sens pour les notifications ponctuelles, le champ `lu` in-app n'a
pas de sens pour Mattermost). La séparation reflète trois cycles de vie
réellement différents.

### 2.8 Configuration en table clé/valeur

`configuration_parametres` implémente ce que le diagramme de classes
avait choisi de ne pas modéliser (§2.3 du README des diagrammes). Le
schéma opte pour un stockage clé/valeur avec `valeur: JSONB`, ce qui
donne trois avantages :
- **Ajouter un paramètre ne demande pas de migration** — juste un
  `INSERT`.
- **Les paramètres composites** (politique de verrouillage avec
  tentatives max + délai, politique de mot de passe avec plusieurs
  règles) tiennent naturellement dans un objet JSON.
- **La table reste petite** — une dizaine de lignes en tout, dont les
  10 valeurs par défaut sont insérées à la fin du fichier SQL, chaque
  ligne annotée du code d'exigence dans sa description.

Contrepartie : les paramètres sont typés côté application, pas côté
base — un développeur peut techniquement stocker une chaîne
"pas_un_nombre" dans un champ qui devrait être un entier. Le seed
initial et la couche de service prennent en charge la validation.

### 2.9 Journal d'audit en lecture seule au niveau base

`journal_audit` implémente NFR-SEC-03 et l'interface de consultation
EF-CFG-03→06. Deux mécanismes protègent son intégrité (EF-CFG-06) :
- **Contrôle applicatif** : aucun endpoint de l'API n'expose de
  modification ou de suppression sur cette table.
- **Défense en profondeur au niveau base** : deux `CREATE RULE ... DO
  INSTEAD NOTHING` interceptent silencieusement tout `UPDATE` ou
  `DELETE` qui parviendrait à la table, y compris si un attaquant
  compromettait la couche applicative.

C'est le seul endroit du schéma où une contrainte d'intégrité vit à
la fois côté application ET côté base. La duplication est intentionnelle
— la valeur probante du journal justifie ce coût.

`en_delegation BOOLEAN` et `delegation_id` matérialisent EF-AUTH-14 (les
actions effectuées en délégation sont traçables comme telles, distinctes
des actions de l'Admin principal).

L'index `idx_journal_audit_recherche_trgm` utilise l'extension `pg_trgm`
pour la recherche texte libre EF-CFG-04 — mêmes mécanismes que les
recherches employés (EF-EMP-12) et candidatures (EF-REC-06).

### 2.10 Suppressions logiques par colonne `statut`

Le schéma respecte strictement NFR-DATA-01 : aucune entité métier
(`employes`, `candidatures`, `departements`, `demandes_administratives`,
`offres_emploi`) n'expose de suppression physique. Toutes utilisent une
colonne `statut` de type ENUM (`actif`/`inactif`, `ouverte`/`fermee`,
etc.) pour la désactivation logique.

Les seules `ON DELETE CASCADE` du schéma portent sur des relations
parent-enfant strictes où la suppression de l'enfant n'a aucun sens
sans le parent (`employe_transferts` cascade sur `employes`,
`entretiens` cascade sur `candidatures`, `sessions_utilisateur` cascade
sur `utilisateurs`). Aucune cascade destructrice sur des entités
métier.

### 2.11 Recherche texte libre via `pg_trgm`

Trois index GIN utilisant l'extension `pg_trgm` implémentent les trois
exigences de recherche texte libre du cahier des charges :
- `idx_employes_recherche_trgm` (EF-EMP-04, EF-EMP-12) sur nom/prénom/email
- `idx_candidatures_recherche_trgm` (EF-REC-06) sur nom/prénom/email
- `idx_journal_audit_recherche_trgm` (EF-CFG-04) sur action/entité/details

Aucune dépendance à Elasticsearch ou un autre moteur externe — la
recherche partielle et fautes de frappe sont couvertes par les
trigrammes natifs de PostgreSQL. C'est une décision qui simplifie
significativement l'architecture (une base à opérer, pas deux).

### 2.12 `plannings_teletravail` — planning récurrent, interrogé au calcul

`plannings_teletravail` implémente EF-ATT-08→10. Il matérialise le
télétravail hybride : par employé, les jours de la semaine travaillés à
distance sur une période donnée (`date_debut`, `date_fin` nullable pour
un planning à durée indéterminée).

**Modèle physique : une ligne par jour de télétravail plutôt qu'une
colonne bitmask ou un tableau.** Un employé qui télétravaille le lundi
et le mercredi a deux lignes (`jour_semaine = 'lundi'` et
`jour_semaine = 'mercredi'`) pointant vers le même planning parent. Cela
donne trois avantages concrets :

- **La question "cet employé est-il en télétravail ce jour-là ?"
  devient un simple `EXISTS` avec index composite**
  (`employe_id`, `jour_semaine`), sans manipulation de bitmask ni de
  tableau.
- **Un `CHECK` de plage est trivial** : `jour_semaine` est un ENUM
  (`type_jour_semaine` = `lundi`..`dimanche`), impossible de saisir une
  valeur invalide.
- **L'audit granulaire est gratuit** : ajouter le mardi à un planning
  existant est un `INSERT` d'une ligne, journalisable comme n'importe
  quel autre changement (NFR-SEC-03).

**Un planning parent, plusieurs jours, plusieurs plannings successifs.**
La table `plannings_teletravail` porte les métadonnées de période
(`employe_id`, `date_debut`, `date_fin`, `cree_par`, `cree_le`), et une
table de détail `plannings_teletravail_jours` porte les jours associés.
Un employé peut avoir plusieurs plannings successifs (période probatoire
sans télétravail, puis passage en hybride) ; comme pour
`employe_transferts` (§2.3), c'est un pattern historisé —
l'ancien planning est conservé pour l'audit, le nouveau prend le
relais. La contrainte applicative "au plus un planning actif à un
instant T" est portée par la couche service, pas par SQL : une
contrainte d'exclusion `EXCLUDE USING gist` sur des `daterange` serait
techniquement possible mais introduit une dépendance à `btree_gist`
sans bénéfice net à cette échelle.

**Dépendance de comportement, pas de structure.** Le planning est
**interrogé** par la détection d'anomalies (`anomalies_pointage`,
§3.2) plutôt que lié structurellement à elle. C'est exactement le
pattern déjà utilisé pour `jours_feries` par le calcul de solde
(`mouvements_conges`, §2.2) et pour `horaires_reference` par
l'évaluation des retards (§2.3) : la donnée référentielle est lue au
moment du calcul, pas jointe en dur.

Concrètement, la règle métier EF-ATT-04/09 se traduit ainsi :
lorsqu'on évalue la présence d'un employé pour un jour donné, la
détection d'anomalies vérifie d'abord s'il existe un planning de
télétravail actif à cette date ET une ligne
`plannings_teletravail_jours` correspondant au jour de la semaine — si
oui, aucune anomalie n'est générée (l'absence de scan est le
comportement attendu). Sans planning ou en dehors de la période de
validité, la logique de détection reste inchangée.

**Aucune modification des tables existantes.** `pointages`,
`anomalies_pointage`, `horaires_reference`, `employes` restent inchangés.
Le télétravail est une couche d'information supplémentaire, pas une
refonte de la présence. C'est la garantie que cette extension peut
être ajoutée par une migration Flyway additive (une `V3__telework.sql`)
sans toucher au schéma existant.

---

## 3. Divergences assumées par rapport au diagramme de classes

Cette section documente les endroits où schéma et diagramme se
contredisent, avec la justification du choix retenu côté schéma.
**Le diagramme de classes n'est pas mis à jour** : il reste tel qu'il
était à la fin de la phase de conception, comme trace de l'intention
originale. Le schéma reflète ce qui est réellement construit ; en cas
de désaccord entre les deux artefacts, **le schéma fait foi**.

### 3.1 Fichiers centralisés — schéma > diagramme

Le diagramme mettait des chemins de fichiers en dur dans plusieurs
classes. Le schéma centralise via `fichiers` (§2.1 ci-dessus). C'est une
amélioration nette, non un tradeoff.

### 3.2 Anomalies de pointage comme entité de premier niveau

Le diagramme incluait la détection d'anomalies comme un `<<include>>`
implicite sur le scan (voir §3.5 du README des diagrammes). Le schéma
crée `anomalies_pointage` comme table dédiée avec ses propres colonnes
(`type_anomalie`, `resolue`, liens vers les pointages d'entrée/sortie).

Justification schéma : les anomalies ont un cycle de vie propre — elles
peuvent être marquées "résolues", filtrées par période, consultées dans
un écran dédié. Une entité de premier niveau reflète mieux ce cycle
qu'un simple événement dérivé.

### 3.3 Candidat et Application fusionnés dans `candidatures`

Le diagramme séparait `Candidate` (identité) et `Application` (statut,
étape du pipeline). Le schéma les unifie dans `candidatures`.

Justification schéma : dans ce système, un candidat n'existe jamais
sans candidature associée — il n'y a pas de "base de candidats"
indépendante à laquelle des candidatures futures se rattacheraient.
La séparation aurait forcé une jointure à chaque affichage sans
apporter de bénéfice réel. `UNIQUE (offre_id, email)` porte la
déduplication NFR-DATA-03 directement.

### 3.4 Certificats de stage et de travail unifiés dans `envois_documents`

Le diagramme argumentait pour la séparation `StageCertificate` /
`WorkCertificate`. Le schéma unifie dans `envois_documents` avec un
discriminateur (§2.6 ci-dessus).

Justification schéma : la structure des deux entités est identique,
seul le comportement métier diffère — et le comportement métier vit
dans l'application, pas dans le schéma. Le déclenchement planifié vs
manuel reste visible via la séparation avec `notifications_planifiees`.

### 3.5 Hiérarchie d'héritage des demandes aplatie

Le diagramme avait `AdministrativeRequest` abstraite avec `LeaveRequest`
et `ExitPermit` concrètes. Le schéma utilise une table unique
`demandes_administratives` avec discriminateur `type_demande` (§2.5
ci-dessus).

Justification schéma : single-table inheritance pour 2-3 sous-types
avec peu de champs spécifiques est la solution la plus pragmatique.
Les CHECK constraints portent l'invariance "tel champ obligatoire
pour tel type" que l'héritage aurait portée par typage.

### 3.6 `entretiens` comme table dédiée

Le diagramme mettait `interviewResult` et `interviewComment`
directement sur `Application`. Le schéma les extrait dans une table
`entretiens` avec sa propre clé, permettant plusieurs entretiens par
candidature (non exigé par les requirements mais gratuit à supporter).

### 3.7 `SystemConfig` construit là où le diagramme l'omettait

Le diagramme choisissait explicitement de ne pas modéliser
`SystemConfig` (§2.3 du README des diagrammes). Le schéma le construit
sous forme de `configuration_parametres` en clé/valeur (§2.8 ci-dessus).

Justification schéma : le diagramme argumentait qu'une "boîte inerte
sans association forte" n'apporterait rien visuellement. Le schéma
n'a pas ce problème — une table de configuration est un pattern
familier, et le stockage clé/valeur avec JSONB est plus flexible qu'un
mapping en colonnes.

### 3.8 Trois tables de notifications au lieu d'une classe

Le diagramme avait `Notification` seule dans le package Cross-Cutting.
Le schéma éclate en `notifications_mattermost`,
`notifications_planifiees` et `notifications_in_app` (§2.7 ci-dessus).

Justification schéma : trois cycles de vie réellement différents
justifient trois tables distinctes plutôt qu'une table polymorphe avec
beaucoup de colonnes nullables.

---

## 4. Ce qui n'est volontairement pas dans le schéma

**Export & Reporting** : aucune table dédiée. Les exports EF-EXP-01/02/03
sont générés à la demande à partir des tables métier existantes
(employés, pointages, demandes) puis matérialisés en Excel/PDF côté
application, sans persistance en base. C'est cohérent avec EF-EXP-04
(pas de job planifié, données au moment de la génération).

**Table `roles`** : `role_utilisateur` reste un ENUM (`admin`,
`manager`), pas une table. Deux valeurs seulement, aucune permission
fine à porter au niveau ligne, l'ENUM est plus simple et plus rapide
au JOIN. Une table dédiée aurait été justifiée si le RBAC
avait des permissions modifiables à chaud, ce qui n'est pas le cas ici.

**Cache de solde de congés** : aucune table de type
`soldes_conges_courants`. Le solde est toujours calculé à la volée
(§2.2, §2.10). Stocker le solde risquait la dérive entre la valeur
stockée et la vérité du ledger — coût inacceptable pour un léger gain
de performance à cette échelle.

**Jetons de session, compteurs de rate limit, tokens de reset**
persistants au-delà de leur durée de vie : les tokens de
réinitialisation sont dans `reinitialisations_mot_de_passe` avec
`expire_le` et `utilise`, mais aucun mécanisme de purge n'est
matérialisé au niveau schéma — c'est une politique applicative à
mettre en place ultérieurement.

**Tables techniques de queue** : n8n gère sa propre file d'attente en
externe. `notifications_planifiees` est notre file d'attente métier
(surveillance stage/CDD), pas une file d'attente technique
générique.

**Données métier réelles de la RH (reprise Excel)** : les fiches
employés, soldes de congés, historique de présence et structure
organisationnelle actuellement tenus en Excel par la RH
**n'entrent jamais via une migration Flyway**. Les migrations SQL
portent uniquement le schéma (V1) et les données de référence
(V2 : horaire par défaut, jours fériés civils marocains, paramètres
système, compte Admin initial). La reprise des données existantes passe
par la **fonctionnalité d'import validée de l'application** (EF-EMP-07 :
Excel/CSV avec validation ligne à ligne, mode dry-run, déduplication par
matricule, idempotence). Ce cloisonnement est structurel : il garantit
que les données historiques passent par les mêmes invariants métier et le
même journal d'audit qu'une saisie courante, et il évite qu'une reprise
mal formée pollue une base neuve sans laisser de trace exploitable.

---

## 5. Traçabilité avec le cahier des charges

Chaque colonne significative du schéma porte son code d'exigence en
commentaire SQL. Les colonnes purement techniques (`id`, `cree_le`,
`modifie_le`, index de performance) n'ont pas de code car elles ne
répondent pas à une exigence spécifique — ce sont les briques
d'infrastructure qui rendent le reste possible.

Les codes d'exigence utilisés sont exactement ceux de
`01-requirements.md` (post-fusion des deux addendums : le premier
couvrant fin de CDD, délégation, notifications in-app, journal d'audit ;
le second couvrant télétravail hybride — EF-ATT-08/09/10 — et reprise
des données Excel). Un `EF-EMP-15` ou `EF-ATT-09` dans un commentaire
renvoie sans ambiguïté à sa définition dans le cahier des charges
principal.

Cette convention permet à un développeur qui lit le schéma sans avoir
le cahier des charges en mémoire de retrouver instantanément le
"pourquoi" d'une colonne, et à l'inverse à un lecteur du cahier des
charges de retrouver l'implémentation physique d'une exigence en
grepant le schéma.
