# Diagrammes UML — Gestion Entreprise RH

Ce document accompagne l'ensemble des diagrammes UML du projet :

- **1 diagramme de classes** (`ClassDiagram_000.png`)
- **1 diagramme de cas d'utilisation de contexte** (`uc-00-overview.puml`)
- **6 diagrammes de cas d'utilisation détaillés**, un par module métier
  (`uc-01-auth-core.puml` à `uc-06-documents-rh.puml`)
- **2 diagrammes de séquence** ciblant les flux à plus forte charge de
  conception (`sd-01-cv-ingestion.puml`, `sd-02-leave-request.puml`)

Il explique **pourquoi les diagrammes sont découpés ainsi**, **quelles
décisions de modélisation ont été prises**, et **ce qui a été volontairement
laissé hors des diagrammes** (couvert en prose dans le rapport plutôt
qu'ajouté comme boîte ou oval de plus).

> **Addendum télétravail & reprise de données.** Cette version intègre
> deux ajouts au périmètre : le **télétravail hybride** (nouvelle classe
> `TeleworkSchedule` dans le package Attendance, cas d'utilisation
> `Gérer planning de télétravail` et impact sur la détection d'anomalies —
> §2.1, §3.5, §5) et la **reprise des données Excel existantes** de la RH
> au démarrage via la couche service d'import, sans classe de domaine
> dédiée (§2.3). Aucun des deux ne modifie les diagrammes de séquence
> existants (cf. §4).

---

## 1. Structure commune : 7 packages métier alignés

Les deux types de diagrammes suivent la même découpe en packages. Cet
alignement est délibéré : chaque diagramme de cas d'utilisation détaillé
peut être lu **côte à côte** avec le package correspondant du diagramme de
classes.

| Package | Diagramme de classes — classes principales | Diagramme de cas d'utilisation |
|---|---|---|
| Auth & Core | `User`, `Department`, `WorkSchedule`, `Role` | `uc-01-auth-core.puml` |
| Employee | `Employee`, `EmployeeDocument`, `EmployeeCard`, `ContractType`, `EmployeeStatus`, `DepartureReason` | `uc-02-employee.puml` |
| Attendance | `QRCode`, `AttendanceRecord`, `TeleworkSchedule`, `ScanType`, `Weekday` | `uc-03-attendance.puml` |
| Recruitment | `JobPosting`, `Candidate`, `Application`, `AIAnalysis`, `CandidateStatus`, `InterviewResult`, `AIAnalysisStatus` | `uc-04-recruitment.puml` |
| Administrative Requests | `AdministrativeRequest`, `LeaveRequest`, `ExitPermit`, `LeaveMovement`, `Holiday`, `RequestType`, `RequestStatus`, `LeaveGranularity` | `uc-05-admin-requests.puml` |
| Documents RH | `StageCertificate`, `WorkCertificate` | `uc-06-documents-rh.puml` |
| Cross-Cutting | `Notification`, `AuditLog`, `NotificationType` | (transversal, pas de diagramme dédié) |

Le package **Cross-Cutting** est présent uniquement côté classes, car les
notifications et le journal d'audit sont transversaux : ils n'appartiennent
à aucun module métier particulier et sont utilisés par tous. Côté cas
d'utilisation, cette transversalité se traduit par des ovals `Notifier via
Mattermost` répétées dans plusieurs diagrammes (Recrutement, Demandes,
Documents RH), et par le fait que **Mattermost** apparaît comme acteur
secondaire dans le diagramme de contexte.

---

## 2. Diagramme de classes

### 2.1 Décisions structurantes

#### `User` séparé de `Employee`

Un `User` est une identité de connexion (Admin ou Manager) ; un `Employee`
est une personne dont on gère le dossier RH. Les deux sont volontairement
séparés :
- Tous les employés ne sont pas des utilisateurs (les employés non-cadres
  ne se connectent pas — cf. §1.1 du cahier des charges).
- Un `User` peut correspondre à un `Employee` (association `is linked to`
  avec cardinalité `0..1` des deux côtés) mais ce n'est pas obligatoire.

Cette séparation évite le piège classique de coupler l'identité de
connexion au cycle de vie RH — un employé qui quitte l'entreprise devient
`INACTIVE`, son compte utilisateur associé est désactivé, mais les deux
opérations restent indépendantes et traçables.

#### Registre de mouvements pour le solde de congés

`LeaveRequest` ne stocke pas de solde. Le solde est calculé à la volée par
`Employee.getLeaveBalance()`, qui somme les `LeaveMovement` (deltas
signés) et l'accumulation mensuelle depuis `startDate`.

Ce choix — un ledger plutôt qu'un champ mutable — est structurant : il
garantit la traçabilité de chaque décrément/recrédit, permet de rejouer
l'historique en cas de contestation, et évite les problèmes de dérive
qu'un champ `currentBalance` stocké et modifié à la main introduirait
inévitablement. C'est la contrepartie d'un léger surcoût de calcul, jugé
négligeable à l'échelle d'une entreprise de la taille de HB Développement.

#### `AdministrativeRequest` abstraite, `LeaveRequest`/`ExitPermit` concrètes

`AdministrativeRequest` porte les champs communs à toute demande
(`type`, `status`, `motif`, dates de revue, `reviewedBy`). Les deux
sous-classes ajoutent leurs spécificités :
- `LeaveRequest` : granularité, dates de début/fin, jours consommés.
- `ExitPermit` : date de sortie, heures de départ et retour prévues.

Cette hiérarchie permet l'extensibilité demandée par EF-ADM-07 (ajout de
nouveaux types de demande sans modification structurelle) sans imposer un
héritage aux types qui n'existent pas encore — un futur type "avance sur
salaire" hériterait simplement de `AdministrativeRequest` avec ses
propres champs.

#### `TeleworkSchedule` séparé de `WorkSchedule` — deux notions distinctes

Le télétravail hybride (EF-ATT-08→10) introduit une nouvelle classe
`TeleworkSchedule` dans le package Attendance. Il est **impératif de ne
pas la confondre** avec la classe `WorkSchedule` déjà présente dans le
package Auth & Core : ce sont deux notions différentes.

- **`WorkSchedule`** est l'**horaire de référence de l'entreprise**
  (EF-ATT-07) : une seule configuration valable pour tous, portant les
  heures de début/fin et la tolérance, historisée par `effectiveFrom`.
  Elle répond à la question « à quelle heure est-on attendu ? ».
- **`TeleworkSchedule`** est un **planning récurrent propre à un
  employé** : les jours de la semaine où cet employé travaille à
  distance, avec `startDate` et `endDate: Date [0..1]` (fin optionnelle =
  planning à durée indéterminée). Elle répond à la question « tel jour,
  cet employé est-il attendu sur site ou non ? ».

`TeleworkSchedule` est modélisée comme classe à part entière plutôt que
comme attribut de `Employee`, pour les mêmes raisons qui ont justifié la
séparation de `QRCode` :
1. Elle a son propre cycle de vie et sa propre période de validité
   (`startDate`/`endDate`), indépendante du dossier employé.
2. Un employé peut ne pas en avoir (association `0..1` — comportement par
   défaut : présence sur site tous les jours ouvrés), en avoir une active,
   ou en avoir eu de successives (l'ancienne conservée pour l'historique).
3. Les jours télétravaillés sont exprimés via l'énumération `Weekday`
   (association `TeleworkSchedule → Weekday`, `1..*`) — un employé en
   hybride télétravaille au moins un jour, sans quoi le planning n'existe
   pas.

La règle métier structurante que cette classe porte : un jour de
télétravail planifié est du **temps travaillé à distance**, ni absence ni
congé. Elle est consommée par la détection d'anomalies — la méthode
`AttendanceRecord.isAnomaly()` (ou son équivalent service) lit le
`TeleworkSchedule` de l'employé et **court-circuite toute détection** si
le jour scanné (ou non scanné) tombe sur un jour télétravaillé actif
(EF-ATT-04/09). C'est une dépendance de **comportement au moment du
calcul**, analogue à la lecture de `Holiday` par
`computeDaysConsumed()` (cf. §2.1 « `Holiday` sans association
formelle ») : `TeleworkSchedule` n'est pas lié structurellement à
`AttendanceRecord`, il est **interrogé** lors de l'évaluation d'anomalie.

#### QR code séparé de l'employé

`QRCode` est une classe à part entière plutôt qu'un attribut de
`Employee`, pour trois raisons :
1. Il a son propre cycle de vie (`isActive`, `blockedAt`, `blockedBy`).
2. Il peut être régénéré (un nouveau QR remplace l'ancien, l'ancien reste
   dans l'historique bloqué).
3. `AttendanceRecord` référence le QR utilisé au scan, pas l'employé
   directement — utile pour tracer les scans effectués avec un QR
   ultérieurement bloqué (détection d'abus).

#### `AIAnalysis` séparé de `Application`

L'analyse IA d'un CV est représentée comme entité distincte
(`Application analysed by AIAnalysis`, cardinalité `0..1`) plutôt que
comme champs directement dans `Application`. Justifications :
- L'analyse peut échouer (`status = FAILED`) sans bloquer la candidature.
- Elle peut être relancée (`retry()`), auquel cas un nouvel `AIAnalysis`
  remplace l'ancien.
- Les champs extraits (`extractedFirstName`, etc.) sont réutilisés pour
  pré-remplir une fiche employé à l'embauche (EF-EMP-05) — les garder
  regroupés dans une classe dédiée facilite cette réutilisation.

#### Deux certificats parallèles dans Documents RH

`StageCertificate` et `WorkCertificate` ont une structure quasi identique
(mêmes attributs, mêmes méthodes `generate()` et `send()`). Ils sont
volontairement modélisés comme deux classes distinctes plutôt qu'une
seule classe paramétrée, pour deux raisons :
- Ils obéissent à des règles métier différentes (surveillance planifiée vs
  déclenchement manuel — cf. le diagramme de cas d'utilisation Documents RH
  §3.6 ci-dessous).
- Ils ont chacun leur association propre avec `Employee` (`0..1` des deux
  côtés), ce qui reste explicite au lecteur : un employé donné aura reçu
  soit un certificat de stage (s'il était stagiaire), soit un certificat
  de travail (s'il était CDI/CDD), jamais les deux.

Le petit surcoût de duplication de champs est le prix à payer pour un
diagramme qui documente honnêtement les deux types de sortie légale
possibles au Maroc.

#### `Holiday` sans association formelle

`Holiday` est une classe de référence lue par `LeaveRequest` au moment du
calcul des jours consommés (un jour férié tombant dans un congé approuvé
n'est pas décompté du solde, cf. §3.4 du cahier des charges). Mais aucune
association UML ne relie `Holiday` à `LeaveRequest` :
- Une association impliquerait une dépendance structurelle persistante
  (un `LeaveRequest` "possède" ou "référence" des `Holiday` précis).
- La réalité est une dépendance de **requête** au moment du calcul :
  `computeDaysConsumed()` lit les `Holiday` dans la plage de dates du
  congé, sans que le résultat ne modifie ces jours fériés ni ne les lie
  durablement au congé.

Modéliser cette dépendance comme association donnerait une fausse
impression de persistence. Elle est documentée par un commentaire attaché
à la méthode `computeDaysConsumed()`, ce qui reflète la réalité : c'est
une dépendance de comportement, pas de structure.

### 2.2 Contraintes légales matérialisées dans le modèle

Trois règles du droit marocain du travail sont directement visibles dans
la structure :

- **`ContractType` distingue `STAGIAIRE` et `STAGIAIRE_REMUNER`** : les
  deux types ne donnent pas droit à congé payé (`getLeaveBalance()`
  retourne 0), mais génèrent tous deux un certificat de stage à la fin.
  Cette distinction est légale et non pas juste économique.
- **`WorkCertificate` réservé aux CDI et CDD** : la méthode `generate()`
  refuse la création pour un employé de type `STAGIAIRE*`. C'est modélisé
  comme règle de comportement plutôt que comme contrainte structurelle
  (les deux classes ne sont pas liées à une sous-classe de `Employee`)
  parce que le type de contrat est un attribut mutable — un stagiaire peut
  être promu CDI, auquel cas il devient éligible au certificat de travail.
- **`LeaveMovement` avec `deltaDays: Float`** : la granularité `0.5` permet
  les demi-journées (matin/après-midi), obligation posée par la §1.1 du
  cahier des charges. Un `Integer` aurait interdit ce cas d'usage.

### 2.3 Ce qui n'est volontairement pas dans le diagramme de classes

**Politiques cross-cutting non persistantes** : jetons de session,
compteurs de rate limit, tokens de réinitialisation de mot de passe. Ce
sont des mécanismes de sécurité (NFR-SEC-08/09, EF-AUTH-08/09/10) qui n'ont
pas de représentation métier durable — leur état vit dans le cache ou dans
des tables techniques éphémères. Les inclure gonflerait le diagramme sans
apporter d'information de conception métier.

**Reprise de données Excel / import en masse** : la reprise des feuilles
Excel existantes de la RH au démarrage (EF-EMP-07, cf. §1.1 du cahier des
charges) et, plus généralement, l'import en masse ne donnent lieu à
**aucune classe de domaine**. La validation ligne à ligne, le mode
simulation (dry-run), la déduplication par matricule et l'idempotence
sont des responsabilités de la **couche service d'import** : elles
produisent des instances des classes de domaine existantes (`Employee`,
`Department`, `LeaveMovement`, `AttendanceRecord`…) mais ne constituent
pas une entité persistante propre. Un éventuel journal d'import (batch,
lignes acceptées/rejetées) relève d'une table technique, au même titre
que les mécanismes de sécurité éphémères ci-dessus, et non du modèle
métier. Ce point est structurant pour la conception : la migration passe
par le **même chemin validé** que les imports courants, et non par une
injection SQL directe qui court-circuiterait les invariants du domaine.

**`SystemConfig`** : un écran de configuration centralisé (EF-CFG-01)
consolide tous les paramètres système. Sa classe correspondante serait un
singleton sans comportement propre (juste des champs lus par d'autres
services). Le représenter dans le diagramme ajouterait une boîte inerte
sans association forte à quoi que ce soit — les paramètres qu'elle
contient sont documentés dans le cahier des charges, c'est suffisant.

**Module Export & Reporting** : les exports (EF-EXP-01/02/03) sont des
générations de fichiers à la demande, sans état persistant côté serveur.
Aucune classe métier à modéliser — la fonctionnalité relève de la couche
service, pas du domaine.

**Génération de carte employé en lot** : `EmployeeCard` existe déjà avec sa
méthode `generate()`. La génération en lot (EF-EMP-14) est une itération
sur cette méthode côté service, pas une nouvelle entité.

**Transfert d'employé entre départements** : `Employee.department` est
déjà mutable ; l'historisation du transfert (EF-EMP-11) est un aspect
d'audit couvert par `AuditLog`, pas une classe distincte.

**Email de rejet candidat** : représenté comme méthode
`Application.notifyRejection()` plutôt que classe `RejectionEmail`. Un
email de rejet n'a pas d'état persistant propre — c'est un événement
horodaté dans le journal d'audit, avec un corps de message temporaire
édité au moment de l'envoi. Créer une classe pour un événement sans état
serait de la sur-modélisation.

**`Notification` in-app (module EF-NOTIF de l'addendum)** : non inclus car
l'addendum n'est pas encore validé avec l'encadrant. Si retenu, il
donnerait naissance à une classe `InAppNotification` dans le package
Cross-Cutting, en parallèle de `Notification` (Mattermost) — les deux
partageraient probablement une classe abstraite parente `Notification`
avec deux sous-classes concrètes.

---

## 3. Diagrammes de cas d'utilisation

### 3.0 Pourquoi 7 diagrammes plutôt qu'un seul

Un premier essai a été fait avec un diagramme unique couvrant les 6
modules (~30 cas d'utilisation, 6 acteurs). Résultat : illisible à toute
échelle d'impression ou d'écran, et impossible à maintenir proprement dans
Astah.

La découpe retenue suit exactement la structure des packages du diagramme
de classes détaillée au §1, plus **1 diagramme de contexte** qui donne la
vue d'ensemble sans le détail. Chaque cas d'utilisation d'un diagramme
détaillé devient ainsi candidat naturel à un diagramme de séquence
ultérieur, avec les classes de son package pour "peupler" la séquence.

### 3.1 Acteurs — primaires vs secondaires

Le cahier des charges (§1.1) ne définit que 2 acteurs **connectés** au
système : Admin RH et Manager. Mais 4 autres intervenants déclenchent ou
reçoivent des cas d'utilisation sans jamais se connecter — ils sont
modélisés comme acteurs **secondaires** pour rester fidèles au
comportement réel du système, pas seulement à son écran de login.

| Acteur | Type | Rôle |
|---|---|---|
| **Admin RH** | Primaire | Gère tout : employés, présence, recrutement, demandes, documents |
| **Manager** | Primaire | Lecture scoping à son département + saisie entretien + décon/connexion |
| **Employé** | Secondaire, non connecté | Scanne son badge au kiosque uniquement (EF-ATT-01) |
| **n8n** | Secondaire, externe | Déclenche l'ingestion de candidatures et la surveillance de fin de stage (workflows planifiés) |
| **Service IA** | Secondaire, externe | Analyse les CV, retourne un score et une justification |
| **Mattermost** | Secondaire, externe | Réceptionne les notifications sortantes |

Ce choix a une conséquence directe sur la lecture des diagrammes : un cas
d'utilisation relié à **n8n** ou **Service IA** représente un comportement
**automatique**, pas une action humaine — il ne doit pas être confondu avec
une fonctionnalité que l'Admin RH doit déclencher manuellement.

### 3.2 Diagramme 00 — Vue d'ensemble

**Fichier :** `uc-00-overview.puml`

Diagramme de contexte : 6 cas d'utilisation "parapluie" (un par module) et
tous les acteurs. Objectif : donner en un coup d'œil qui interagit avec
quel sous-système, sans noyer le lecteur dans le détail des 30 cas
d'utilisation réels.

Deux relations méritent d'être expliquées :
- **`Gerer recrutement → Gerer dossier employe`** ("crée fiche employé") :
  matérialise le seul flux qui traverse deux modules — l'embauche d'un
  candidat génère automatiquement une fiche employé (EF-EMP-05 /
  EF-REC-13). C'est la seule flèche inter-module du diagramme,
  volontairement, pour ne pas noyer le message.
- **Modules → Mattermost** : montre que 4 des 6 modules produisent des
  notifications sortantes (Auth via AuditLog, Recrutement, Demandes
  Administratives, Documents RH), ce qui justifie a posteriori le package
  `Cross-Cutting / Notification` du diagramme de classes.

Ce diagramme est destiné à une lecture rapide (page de garde d'un dossier
de spécifications, slide de kickoff) — il ne remplace aucun des 6
diagrammes détaillés.

### 3.3 Diagramme 01 — Auth & Core

**Fichier :** `uc-01-auth-core.puml`

Couvre l'authentification et l'administration système (EF-AUTH-01 à
EF-AUTH-07, EF-ATT-07, EF-DASH-01/02, EF-CFG-01).

**Décision de modélisation :** `Consulter tableau de bord` est inclus bien
que MoSCoW = *Could*, car c'est un vrai objectif d'acteur, indépendant de
sa priorité de livraison — un diagramme de cas d'utilisation documente le
**besoin**, pas le planning. Le Manager y a un accès en **lecture réduite
à son département** (EF-DASH-02) : cette restriction n'apparaît pas comme
un cas d'utilisation séparé (ce serait sur-modéliser une règle de portée
RBAC), elle est documentée par une note attachée au cas d'utilisation.

**Consolidation de la configuration :** l'ancienne oval `Configurer
horaire de référence` a été renommée en `Configurer paramètres système`
pour matérialiser la décision de conception EF-CFG-01 — un écran unique
regroupe tous les paramètres système (horaire de référence et tolérances,
calendrier des jours fériés, seuil et fenêtre de rétention pour la
réactivation IA, politique de verrouillage de compte, complexité de mot de
passe). Ce n'est pas 5 cas d'utilisation distincts, c'est **un seul
objectif d'acteur** — "paramétrer le système" — dont le contenu est
documenté par la note attachée à l'oval.

Aucune relation `<<include>>`/`<<extend>>` dans ce module : les 5 cas
d'utilisation sont indépendants les uns des autres.

**Non modélisés volontairement dans ce diagramme** (couverts en prose du
rapport) : la réinitialisation de mot de passe par e-mail (EF-AUTH-08), le
verrouillage de compte après échecs successifs (EF-AUTH-09) et
l'expiration de session par inactivité (EF-AUTH-10). Ce sont des
mécanismes standards d'authentification qui ne portent aucune décision de
conception spécifique à ce projet — les représenter comme cas
d'utilisation ajouterait du bruit sans apporter d'information.

### 3.4 Diagramme 02 — Dossier Employé

**Fichier :** `uc-02-employee.puml`

Couvre le cycle de vie complet de la fiche employé (EF-EMP-01, 02, 04,
05, 07, 08, 09, 10).

**Relation clé — désactivation avec vérification :** `Désactiver fiche
employé` et `Désactiver département` **incluent** tous deux le même cas
d'utilisation `Vérifier employés rattachés`. Ce partage volontaire n'est
pas une simplification cosmétique : la règle métier est identique
(bloquer la désactivation tant que des employés actifs sont rattachés à
l'entité en cours de désactivation), seul le déclencheur diffère (fiche
employé Manager vs département entier — EF-EMP-08 et EF-EMP-10).
Modéliser deux inclusions vers la même cible plutôt que dupliquer le cas
d'utilisation respecte le principe DRY appliqué aux règles métier.

**CRUD Département :** l'ajout de `Créer/Modifier département` et
`Désactiver département` (EF-EMP-10) reflète le fait qu'un département
est une entité de premier niveau dans le diagramme de classes, dotée de
son propre cycle de vie — pas simplement un attribut de l'employé. La
consolidation Créer/Modifier en une seule oval reste une simplification
de lisibilité justifiée car ces deux actions partagent la même interface
et les mêmes règles de validation.

**Lien inter-module :** `Générer fiche employé depuis candidat` est
déclenché depuis le module Recrutement (`Décider embauche / rejet`, voir
§3.6). Ce cas d'utilisation apparaît donc dans **les deux** diagrammes
détaillés — ce n'est pas une duplication accidentelle, c'est la
représentation honnête d'un flux qui traverse une frontière de module.

**Non modélisés volontairement dans ce diagramme** (couverts en prose) :
la génération de carte employé numérique/imprimable (EF-EMP-09) reste
représentée par une seule oval `Générer / régénérer carte employé` sans
distinguer les deux formats, car le format de sortie est une option de
génération et non un objectif d'acteur séparé ; la génération en lot
(EF-EMP-14) suit la même logique. Le transfert d'employé entre
départements (EF-EMP-11) n'apparaît pas non plus séparément — c'est une
spécialisation de `Modifier fiche employé`. La recherche texte libre
(EF-EMP-12) est un mode d'utilisation de `Consulter liste des employés`,
pas un cas distinct. La notification Manager à la création (EF-EMP-13)
suit le même pattern que les autres notifications Mattermost déjà
visibles ailleurs — la répéter ici alourdirait le diagramme sans nouvelle
information. La consultation du planning de télétravail depuis la fiche
employé (EF-ATT-10) n'apparaît pas non plus ici comme cas distinct : la
gestion du planning est un cas d'utilisation du diagramme Présence
(`Gérer planning de télétravail`, §3.5), et son affichage en lecture
seule sur la fiche est un mode de `Consulter fiche employé`, pas un
objectif d'acteur séparé.

### 3.5 Diagramme 03 — Présence

**Fichier :** `uc-03-attendance.puml`

Couvre le pointage QR (EF-ATT-01 à EF-ATT-06) et le planning de
télétravail hybride (EF-ATT-08 à EF-ATT-10).

**Particularité :** c'est le seul diagramme où un acteur **non connecté**
(Employé) est le déclencheur direct d'un cas d'utilisation
(`Scanner badge`). Ce cas **inclut** systématiquement
`Détecter anomalie de pointage`, qui est un contrôle automatique exécuté
à chaque scan, pas une action distincte que quelqu'un demande.

**Télétravail hybride (EF-ATT-08→10) :** l'Admin RH obtient un cas
d'utilisation `Gérer planning de télétravail` (définir, par employé, les
jours de la semaine travaillés à distance sur une période donnée). Ce cas
appartient à l'Admin (le système n'infère jamais le planning, EF-ATT-08),
et le planning est consultable en lecture seule sur la fiche employé
(EF-ATT-10) — cette consultation n'est pas un cas d'utilisation séparé,
c'est un mode d'affichage de `Consulter fiche employé`, documenté par une
note.

**Impact sur `Détecter anomalie de pointage` :** ce cas d'utilisation
**inclut** désormais `Vérifier planning de télétravail` — le contrôle
consulte le `TeleworkSchedule` de l'employé avant d'évaluer toute
anomalie, et court-circuite la détection si le jour concerné est un jour
télétravaillé actif (EF-ATT-04/09). L'inclusion est modélisée
explicitement car elle porte une vraie décision de conception : sans elle,
tout employé en hybride génèrerait des anomalies erronées (absence de scan
traitée comme absence de check-in) dès le démarrage. C'est la même logique
d'« interrogation au moment du calcul » que celle décrite pour
`TeleworkSchedule` au §2.1 — la note attachée au cas d'utilisation
rappelle qu'un jour télétravaillé est du temps travaillé à distance, ni
absence ni congé.

`Corriger pointage manuellement` reste priorité *Could* mais avec
traçabilité obligatoire (champs `isManualCorrection`, `correctedBy` du
diagramme de classes) — c'est pour ça qu'il reste un cas d'utilisation
Admin à part entière plutôt qu'un simple champ éditable.

### 3.6 Diagramme 04 — Recrutement

**Fichier :** `uc-04-recruitment.puml`

Le module le plus dense (4 acteurs, 12 cas d'utilisation) car c'est le
seul qui orchestre un pipeline externe complet : email → n8n → IA →
Admin RH → Manager.

Quatre relations structurent le diagramme :
- **`Ingérer candidature` (n8n) inclut `Analyser CV`** — l'ingestion et
  l'analyse sont un seul flux automatique déclenché par email entrant,
  pas deux actions séparées demandées par un humain.
- **`Faire progresser candidature` inclut `Notifier Manager`** — mais
  uniquement au passage vers l'étape "Entretien" (EF-REC-08), pas à
  chaque changement de statut. Encore une inclusion conditionnelle à
  confirmer au niveau séquence.
- **`Décider embauche / rejet` étend vers `Générer fiche employé`** —
  relation `<<extend>>` (et non `<<include>>`) car ce comportement ne se
  déclenche que dans **un seul cas** parmi les issues possibles de la
  décision (statut final = "Embauché"), ce qui est exactement la
  sémantique d'`<<extend>>` en UML : comportement optionnel greffé sur
  un point d'extension, par opposition à `<<include>>` qui est un
  sous-comportement toujours exécuté.
- **`Décider embauche / rejet` inclut `Notifier candidat rejeté par
  e-mail`** — inclusion **conditionnelle** au statut final "Rejeté"
  (EF-REC-14). Le choix `<<include>>` plutôt qu'`<<extend>>` ici mérite
  une justification : contrairement à l'embauche (comportement optionnel
  qui *peut* se déclencher), la décision de rejet aboutit
  *systématiquement* à une notification e-mail — les deux branches
  (embauche/rejet) sont symétriques du point de vue métier, mais
  asymétriques en UML car "embauche" ouvre un flux vers un autre module
  (`<<extend>>`) tandis que "rejet" reste dans le même flux
  (`<<include>>`). Le corps du message est éditable avant envoi, ce qui
  reste un détail d'interface non modélisé ici.

### 3.7 Diagramme 05 — Demandes Administratives

**Fichier :** `uc-05-admin-requests.puml`

Couvre congés, bons de sortie, documents libres et calendrier des jours
fériés (EF-ADM-01 à EF-ADM-10).

**Point de vigilance modélisé explicitement :** `Créer demande
administrative` **inclut** `Vérifier solde de congés`, mais seulement
quand `type = congé`. Le cahier des charges impose un **double
contrôle** — une vérification côté saisie (EF-ADM-01) et une revalidation
côté serveur au moment de l'approbation (EF-ADM-05). Le diagramme ne
représente qu'une seule inclusion volontairement : les deux contrôles
sont la **même règle métier** appliquée à deux moments différents du
cycle de vie, pas deux cas d'utilisation distincts — le diagramme de
séquence détaillera où et quand chaque vérification a lieu.

**Calendrier des jours fériés (EF-ADM-10) :** modélisé par une seule
oval `Gérer calendrier des jours fériés` plutôt que trois (ajouter /
modifier / supprimer). Ce choix contraste volontairement avec le CRUD
Département du diagramme Employé, qui a droit à ses trois ovals
distinctes — le critère étant : un département est une entité de premier
niveau du diagramme de classes avec règles métier propres (blocage à la
désactivation), un jour férié est une simple entrée de table de
référence sans règle de cycle de vie. Éclater son CRUD en trois cas
d'utilisation serait de la sur-modélisation. La règle métier importante
— un jour férié tombant dans un congé approuvé n'est pas décompté du
solde — est documentée par la note attachée à l'oval, car c'est le seul
aspect du calendrier qui a un impact sur le reste du système. La gestion
manuelle (pas d'API externe) est justifiée par la nature mobile des
fêtes hégiriennes marocaines, confirmées officiellement quelques jours
seulement à l'avance.

`Consulter historique des demandes` est accessible au Manager en lecture
seule, **sans droit d'approbation** — volontairement absent des flèches
Manager vers `Approuver / rejeter`.

### 3.8 Diagramme 06 — Documents RH

**Fichier :** `uc-06-documents-rh.puml`

Couvre le certificat de stage automatisé et le certificat de travail
manuel (EF-DOC-01 à EF-DOC-11).

**Deux flux parallèles, un canal de sortie partagé :** le diagramme
représente deux lignes de vie de documents distinctes qui convergent sur
`Envoyer e-mail` :
- **Certificat de stage** : surveillance planifiée (n8n) → notification
  Mattermost → confirmation Admin → génération PDF → envoi. Concerne les
  Stagiaires et Stagiaires rémunérés uniquement.
- **Certificat de travail** : déclenchement manuel Admin depuis la fiche
  employé au moment du départ → génération PDF → envoi. Concerne les CDI
  et CDD uniquement. Pas de surveillance planifiée car la date de départ
  n'est pas prévisible (démission, licenciement, rupture peuvent
  survenir à tout moment).

Cette parallélisation reflète une contrainte légale du droit marocain :
tout salarié CDI/CDD a droit à un certificat de travail à sa sortie,
mais son déclenchement diffère fondamentalement d'un certificat de
stage — le diagramme rend cette différence visible plutôt que de la
masquer sous une oval générique "Générer certificat". Elle est aussi
matérialisée dans le diagramme de classes par deux classes distinctes
`StageCertificate` et `WorkCertificate` (§2.1 ci-dessus).

**Règle métier structurante — surveillance stage :** `Surveiller fin de
stage` (n8n, planifié quotidiennement) **inclut** `Notifier Admin via
Mattermost`, mais cette notification est **unique** — envoyée une fois à
J-3 ouvrables, sans relance automatique (EF-DOC-02). C'est un choix
produit délibéré (éviter le spam de notifications) qu'il fallait
documenter ici plutôt que de le laisser implicite, car un lecteur
pourrait naturellement s'attendre à des relances répétées vu le pattern
"surveillance planifiée" du reste du système.

**Réutilisation du canal e-mail :** `Confirmer envoi du certificat`,
`Envoyer document libre à un employé` et `Générer certificat de travail`
incluent tous les trois `Envoyer e-mail` — un cas source peut inclure
plusieurs cas cibles, et réciproquement plusieurs cas sources peuvent
inclure la même cible. Cette convergence n'est pas un accident de
modélisation : elle documente le fait qu'il n'existe qu'un seul canal
d'envoi e-mail sortant dans le système (délégué à n8n, cf. NFR-OPS-07),
ce qui a un impact direct sur l'architecture (un seul point d'échec, une
seule configuration SMTP, un seul point de journalisation).

**Non modélisés volontairement dans ce diagramme** (couverts en prose) :
la saisie de la date de départ et du motif au moment de la désactivation
(EF-DOC-08) est un pré-requis de `Désactiver fiche employé` (module
Employé), pas un cas d'utilisation distinct ; l'affichage du solde de
congés restant à titre informatif (EF-DOC-09) est un détail d'écran ; le
renvoi manuel du certificat de travail (EF-DOC-11, partie renvoi) suit
le même pattern que `Renvoyer certificat de stage` déjà présent.

---

## 4. Diagrammes de séquence

### 4.0 Pourquoi seulement 2 diagrammes de séquence

Un diagramme de séquence a un coût de rédaction et de maintenance élevé
pour un bénéfice qui n'est réel que si le flux comporte une **vraie
décision de conception à défendre** : branchement conditionnel non-trivial,
coordination entre plusieurs systèmes, mécanisme de sécurité ou de
tolérance aux pannes. Modéliser un simple CRUD en diagramme de séquence ne
documente rien que le diagramme de classes ne rende déjà évident.

Deux flux du système répondent à ce critère et méritent leur diagramme :
- **Ingestion CV et analyse IA** (`sd-01-cv-ingestion.puml`) : coordination
  entre 4 systèmes (n8n, API, DB, Service IA), pattern asynchrone,
  branche de dégradation gracieuse en cas d'indisponibilité IA.
- **Création de demande de congé avec double vérification** (`sd-02-leave-request.puml`) :
  double contrôle client + serveur pour se prémunir d'une race condition
  et d'un contournement par appel API direct.

Les autres flux (login, création d'employé, scan de badge, etc.) sont
mécaniquement dérivables du diagramme de classes et du diagramme de cas
d'utilisation correspondants ; les documenter en séquence ajouterait du
volume sans apporter d'information.

### 4.1 Choix de granularité : lignes de vie systèmes

Les deux diagrammes utilisent des lignes de vie au **niveau système**
(n8n, API, DB, Service IA, Frontend, Backend), pas au niveau classe. Ce
choix est délibéré :
- **Objectif** : imprimer l'idée du flux et de ses points de coordination,
  pas décrire une implémentation orientée-objet précise.
- **Bénéfice pratique** : le diagramme reste valide même quand l'équipe
  décide plus tard comment découper le backend en contrôleurs, services et
  repositories — ces décisions internes ne remettent pas en cause le flux
  inter-systèmes.
- **Contrepartie** : les méthodes précises invoquées sur chaque classe ne
  sont pas visibles. Elles restent lisibles dans le tableau de
  correspondances §5 pour ceux qui veulent faire le lien.

### 4.2 Diagramme 01 — Ingestion CV et analyse IA

**Fichier :** `sd-01-cv-ingestion.puml`

Couvre EF-REC-02, EF-REC-04, EF-REC-05.

**Ce que le diagramme rend visible :**

- **Persistance avant appel IA :** l'`Application` et l'`AIAnalysis` (avec
  `status = PENDING`) sont créées **avant** que l'API n'appelle le Service
  IA. Cet ordre est structurant — si on l'inversait, une indisponibilité
  IA ferait perdre la candidature entière. En le respectant, on garantit
  qu'une candidature arrivée par e-mail est toujours retrouvable dans le
  pipeline, quel que soit l'état de l'IA (EF-REC-05).
- **Réponse à n8n avant terminaison IA :** l'API renvoie `201 Created` à
  n8n dès que la persistance initiale est confirmée, **sans attendre** la
  réponse IA. Le workflow n8n se termine ici. C'est la matérialisation
  visuelle de la contrainte NFR-PERF-03 (analyse IA asynchrone, non
  bloquante).
- **Deux branches IA symétriques :** succès → `AIAnalysis.status = DONE`
  avec les champs extraits ; échec ou timeout → `AIAnalysis.status = FAILED`.
  Dans les deux cas, l'`Application` elle-même n'est jamais touchée —
  elle reste consultable et traitable manuellement par l'Admin RH, et
  peut faire l'objet d'une relance IA ultérieure (EF-REC-10).

**Ce que le diagramme ne montre pas volontairement :**
- La comparaison mots-clés avec les candidatures "En attente" (EF-REC-12) :
  déclenchée par la création d'offre, pas par l'ingestion — appartient à un
  autre flux.
- Les détails de parsing e-mail et d'extraction MIME côté n8n : hors
  périmètre système, relèvent de la configuration n8n.

### 4.3 Diagramme 02 — Création de demande de congé avec double vérification

**Fichier :** `sd-02-leave-request.puml`

Couvre EF-ADM-01, EF-ADM-03, EF-ADM-05, NFR-SEC-05.

**Ce que le diagramme rend visible :**

- **Séparation Frontend / Backend :** contrairement au diagramme précédent
  où "API" était une ligne de vie unique, ici Frontend et Backend sont
  séparés parce que **le double contrôle vit précisément entre eux**.
  Traiter l'API comme un tout masquerait la seule chose que ce diagramme
  est censé rendre explicite.
- **Le solde n'est jamais stocké :** les deux calculs (message 5 côté GET,
  message 15 côté POST) s'appuient sur la lecture du registre
  `LeaveMovement` et sur une formule appliquée à la volée. Il n'existe
  pas de champ `currentBalance` à lire ni à mettre à jour — cette absence
  est un choix structurant du diagramme de classes (§2.1), et le diagramme
  de séquence la matérialise en montrant deux **recalculs** plutôt que
  deux **lectures**.
- **Le double contrôle attrape deux échecs différents :**
  - Contrôle Frontend (EF-ADM-01) : attrape les erreurs de saisie et
    empêche une soumission qui échouerait de toute façon — pur bénéfice UX.
  - Contrôle Backend (EF-ADM-05) : attrape le contournement par appel API
    direct (le fameux `curl` bypass mentionné dans NFR-SEC-05) **et**
    attrape le cas de course où une autre demande a été approuvée pour
    le même employé entre le GET et le POST. Le second cas explique
    pourquoi la revalidation se recalcule intégralement plutôt que de
    faire confiance à la valeur envoyée par le Frontend.
- **Branche 409 Conflict :** rend visible que le rejet côté serveur pour
  cause de solde insuffisant a un traitement UX propre — l'utilisateur
  est prévenu que le solde a changé et invité à ressaisir, plutôt que
  de voir une erreur générique.

**Ce que le diagramme ne montre pas volontairement :**
- Le mouvement de recrédit lors d'une annulation (EF-ADM-03) : c'est un
  flux inverse trivial, pas un objectif de démonstration de conception.
- Les contrôles de chevauchement avec des congés déjà approuvés (§3.4 du
  cahier des charges) : règle métier importante mais qui ne présente aucun
  enjeu de coordination inter-systèmes — se traite en une requête SQL
  supplémentaire dans le même flux.
- La vérification du solde côté serveur au moment de l'**approbation**
  d'une demande PENDING existante : la même logique de recalcul
  s'applique, ce serait redondant à diagrammer.

---

## 5. Correspondances cas d'utilisation → classes

Chaque cas d'utilisation détaillé se traduit en une ou plusieurs
opérations sur des classes du diagramme. Quelques correspondances clés,
utiles à garder à l'esprit quand viendront les diagrammes de séquence
(chaque séquence "pêche" dans plusieurs classes et enchaîne leurs
opérations) :

| Cas d'utilisation | Classes impliquées | Méthode(s) principale(s) |
|---|---|---|
| Scanner badge | `QRCode`, `AttendanceRecord`, `Employee`, `WorkSchedule`, `TeleworkSchedule` | `QRCode.isValid()`, `TeleworkSchedule.isRemoteOn(date)`, `WorkSchedule.isLate()`, `AttendanceRecord.computeDuration()` |
| Détecter anomalie de pointage | `AttendanceRecord`, `TeleworkSchedule`, `WorkSchedule` | `TeleworkSchedule.isRemoteOn(date)` (court-circuite), `AttendanceRecord.isAnomaly()` |
| Gérer planning de télétravail | `TeleworkSchedule`, `Employee`, `Weekday`, `AuditLog` | CRUD + `TeleworkSchedule.isRemoteOn(date)`, journalisation de la modification |
| Créer demande de congé | `LeaveRequest`, `Employee`, `LeaveMovement`, `Holiday` | `LeaveRequest.computeDaysConsumed()`, `LeaveRequest.checkBalanceSufficient()`, `Employee.getLeaveBalance()` |
| Approuver / rejeter demande | `AdministrativeRequest`, `LeaveMovement`, `Notification` | `approve()`, `reject()`, `cancel()` |
| Analyser CV (IA) | `Application`, `AIAnalysis`, `JobPosting` | `AIAnalysis.retry()`, `JobPosting.matchPendingCandidates()` |
| Décider embauche / rejet | `Application`, `Employee`, `AIAnalysis` | `Application.advanceStage()`, `Application.notifyRejection()` |
| Désactiver fiche employé | `Employee`, `Department`, `QRCode`, `AdministrativeRequest` | (blocage manuel, non modélisé en méthode dédiée — règle applicative) |
| Confirmer envoi certificat | `StageCertificate`, `Employee` | `generate()`, `send()` |
| Générer certificat de travail | `WorkCertificate`, `Employee` | `generate()`, `send()` |
| Gérer calendrier des jours fériés | `Holiday` | CRUD standard |
| Configurer paramètres système | `WorkSchedule`, `Holiday`, (paramètres divers) | `WorkSchedule.effectiveFrom` pour l'historisation |

---

## 6. Points ouverts non modélisés

Quatre décisions restent ouvertes à date et n'ont volontairement pas été
transformées en cas d'utilisation, en classe ou en séquence — les figer
prématurément dans un diagramme UML donnerait une fausse impression de
certitude. Elles doivent être tranchées avec l'encadrant avant le
démarrage de la réalisation et documentées dans un document de décisions
d'architecture séparé.

### Issues du cahier des charges principal (`01-requirements.md` §7)

1. **Modèle d'authentification du kiosque de pointage** (NFR-UX-02) —
   trois options en présence : réseau local sans authentification, PIN
   partagé, ou jeton par périphérique. Le choix impacte la conception de
   l'endpoint `AttendanceRecord` (authentification requise ou non) et le
   modèle de déploiement du poste kiosque. Sans impact bloquant sur les
   diagrammes UML actuels ; à figer avant l'implémentation du module
   Présence.
2. **Versionnement et export des workflows n8n** (NFR-OPS-05) — modalités
   à définir : stockage dans un dépôt Git dédié, export périodique
   automatisé, ou gestion manuelle. Aucun impact structurel sur les
   diagrammes, purement opérationnel.

### Issues introduites par l'addendum (`02-addendum-requirements.md` §E)

3. **Durée de rétention légale minimale du journal d'audit au Maroc**
   (EF-CFG-06 dans l'addendum) — impacte le dimensionnement du stockage
   et la politique de purge de la table `AuditLog`. À valider avec un
   contact juridique ou l'encadrant. Applicable uniquement si le bloc D
   de l'addendum (consultation du journal d'audit) entre effectivement en
   périmètre.
4. **Périmètre de la délégation Manager en cas de conflit d'intérêt**
   (EF-AUTH-11 dans l'addendum) — quand un Manager reçoit une délégation
   temporaire de droits d'approbation, doit-il être exclu de la
   délégation pour les demandes concernant son propre département, ou
   cette restriction est-elle disproportionnée pour une équipe de cette
   taille ? Applicable uniquement si le bloc B de l'addendum (délégation
   d'approbation) entre effectivement en périmètre.

Les points 3 et 4 se pré-résolvent d'eux-mêmes si l'addendum est reporté
en dette technique post-MVP.
