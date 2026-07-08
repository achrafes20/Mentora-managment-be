# Gestion Entreprise — RH Module
## Document de Conception — Exigences Fonctionnelles et Non Fonctionnelles

**Projet:** Application de gestion d'entreprise, volet RH
**Contexte:** Stage chez HB Développement — outil interne destiné à l'usage RH de l'entreprise
**Statut:** Version consolidée — décisions de conception tranchées avec l'encadrant, addendum initial (suivi CDD, délégation d'approbation, notifications in-app, consultation audit) intégré. Points restants marqués `[À CONFIRMER]` — cf. §7.

---

## 1. Périmètre fonctionnel

Le système couvre neuf modules métier, articulés autour d'un cycle de vie employé complet : un candidat est évalué (Recrutement) → devient un employé (Dossier Employé) → pointe quotidiennement (Présence) → demande des congés ou bons de sortie (Demandes Administratives) → reçoit ses documents RH (Documents RH). Trois modules transverses complètent l'ensemble : export des données consultées à l'écran (Export & Reporting), centralisation des paramètres système et consultation du journal d'audit (Configuration & Paramétrage), et centre de notifications in-app comme canal de repli garanti (Notifications internes).

| Module | Statut |
|---|---|
| Authentification & Contrôle d'accès (RBAC) | Confirmé — module transverse, incluant délégation temporaire d'approbation (cf. §2.1) |
| Dossier Employé | Confirmé — module central, incluant suivi de fin de contrat CDD (cf. §2.3) |
| Présence (pointage QR/badge) | Confirmé |
| Recrutement (ingestion CV + IA) | Confirmé |
| Demandes Administratives (congés, demi-journées, bons de sortie, documents libres) | Confirmé — processus et règles légales confirmés (cf. §1.1) |
| Tableau de bord (compteurs et alertes, Admin + Manager) | Confirmé — minimal, sans graphiques (cf. §2.2) |
| Documents RH (certificat de stage, certificat de travail, envoi de documents libres) | Confirmé — généralisé au-delà du seul certificat de fin de stage, étendu à l'offboarding CDI/CDD (cf. §2.7) |
| Export & Reporting (listes, présences, demandes, audit) | Confirmé — module transverse (cf. §2.8) |
| Configuration & Paramétrage (paramètres + consultation audit) | Confirmé — module transverse (cf. §2.9) |
| Notifications internes (canal in-app parallèle à Mattermost) | Confirmé — module transverse (cf. §2.10) |

### 1.1 Contexte organisationnel et règles confirmées

**Structure organisationnelle :**
- L'entreprise est composée d'au moins 3 départements, chacun doté d'un manager dédié.
- La gestion RH/administrative (approbation des demandes d'absence, bons de sortie, et toute requête administrative) est centralisée auprès d'une seule personne : **Mme Amal Medah (Admin RH)**. Les managers de département n'ont pas de rôle d'approbation — leur rôle dans le système est limité à la consultation des données de leur équipe.
- Le recrutement est géré par la personne RH (réception et sélection des candidatures). Une fois un candidat présélectionné, le processus d'entretien est conduit par le **Manager du département concerné**. Ce rôle est couvert entièrement par le rôle **Manager** dans le système — il n'existe pas de rôle "Chef de Projet" distinct (terminologie unifiée, cf. §2.1).

**Règles légales — solde de congés (droit marocain) :**
- Contrat CDI ou CDD : **1,5 jour ouvrable de congé payé par mois travaillé**, soit 18 jours/an à plein temps (plafond annuel, atteint uniquement si aucun congé n'est pris dans l'année).
- Stagiaire (conventionné) ou Stagiaire rémunéré : **aucun droit à congé payé**.
- Le solde de congés démarre à **0** à la date d'embauche et s'accumule progressivement (jamais un solde disponible en totalité dès le premier jour). Le calcul est effectué **à la volée** (formule : `1,5 × mois travaillés écoulés − jours consommés + jours recrédités`), sans job planifié mensuel ni champ stocké mutable — cf. §3.4 pour le détail du modèle de données (registre de mouvements).
- Un changement de type de contrat en cours d'année (ex. stagiaire promu CDI) **n'est pas recalculé automatiquement** par le système ; l'Admin RH effectue les ajustements nécessaires manuellement (ex. mouvement d'ajustement dans le registre).
- Les demandes de congé peuvent couvrir des **journées complètes ou des demi-journées** (matin / après-midi).

**Outils existants dans l'entreprise :**
- **Mattermost** : messagerie interne, utilisée comme canal de notification (approbation, nouveau candidat, anomalie de pointage, rappel fin de stage) via webhook n8n → Mattermost.
- **Import Excel** : import en masse pour la migration initiale des données, couvrant non seulement les fiches employés mais aussi, à la demande de l'Admin RH, l'historique de présence et les soldes de congés existants — le format d'import doit rester extensible à tout type de donnée que RH souhaite migrer.

**Rôles du système (finalisés) :** Admin (RH) et Manager uniquement. Les employés ne sont pas des acteurs du système (pas de connexion individuelle). Aucun rôle "Recruteur" ni "Chef de Projet" distinct — leurs fonctions sont couvertes respectivement par Admin et Manager.

---

## 2. Exigences fonctionnelles

### 2.1 Authentification & RBAC (EF-AUTH)

- **EF-AUTH-01** : Le système doit permettre la connexion via identifiant/mot de passe pour les rôles Admin et Manager.
- **EF-AUTH-02** : Le système doit restreindre l'accès aux fonctionnalités et données selon le rôle de l'utilisateur connecté, appliqué côté serveur sur chaque endpoint.
- **EF-AUTH-03** : Un Manager doit avoir accès en lecture seule aux données des employés de son département uniquement (dossiers, présences, demandes administratives). Il n'a aucun droit d'approbation ni de modification sur ces modules.
- **EF-AUTH-04** : Un Manager doit avoir accès aux candidatures au stade "Entretien" pour les offres ouvertes dans son département uniquement, afin de consulter le profil candidat et saisir le résultat de l'entretien.
- **EF-AUTH-05** : Un Admin doit avoir accès complet à tous les modules (Employés, Présence, Recrutement, Demandes Administratives) et à la configuration système.
- **EF-AUTH-06** : Le système doit journaliser les tentatives de connexion (réussies et échouées).
- **EF-AUTH-07** : Le système doit permettre à un Admin de créer, modifier et désactiver des comptes utilisateurs (Admin et Manager).
- **EF-AUTH-08** *(nouveau)* : Le système doit permettre à un utilisateur (Admin ou Manager) de réinitialiser son mot de passe en cas d'oubli, via un lien de réinitialisation envoyé par e-mail à l'adresse associée au compte, à durée de validité limitée.
- **EF-AUTH-09** *(nouveau)* : Le système doit verrouiller temporairement un compte après un nombre configurable de tentatives de connexion échouées consécutives (par défaut 5), avec déverrouillage automatique après un délai configurable ou déverrouillage manuel par un Admin.
- **EF-AUTH-10** *(nouveau)* : Le système doit gérer une session utilisateur avec expiration automatique après une période d'inactivité configurable, ainsi qu'une fonction de déconnexion explicite (logout) invalidant le jeton en cours.
- **EF-AUTH-11** *(nouveau — délégation temporaire)* : Le système doit permettre à un Admin de désigner un **délégué temporaire** — un autre compte Admin existant, ou un compte Manager élevé temporairement — avec une **date de début et une date de fin de délégation**, pendant laquelle le délégué obtient les droits d'approbation/rejet des demandes administratives (EF-ADM-02) et de saisie de la décision finale de recrutement. La délégation ne crée pas de nouveau rôle : elle restreint une période de validité sur des droits d'approbation portés par un compte existant.
- **EF-AUTH-12** *(nouveau)* : La délégation accordée à un Manager (EF-AUTH-11) est **scindée** : elle porte uniquement sur les droits d'approbation, jamais sur les droits de gestion des comptes utilisateurs, de configuration système (EF-CFG) ou de désactivation d'employé/département — ces derniers restent réservés au compte Admin principal, délégation ou non.
- **EF-AUTH-13** *(nouveau)* : La délégation doit expirer automatiquement à la date de fin définie, sans action manuelle requise, et rester révocable manuellement à tout moment par l'Admin principal avant l'échéance.
- **EF-AUTH-14** *(nouveau)* : Toute action d'approbation, de rejet ou de décision de recrutement effectuée par un délégué doit être journalisée dans le journal d'audit avec une mention explicite indiquant qu'elle a été réalisée **en délégation** (identité du délégué + identité de l'Admin délégant), distincte d'une action réalisée par l'Admin principal lui-même.
- **EF-AUTH-15** *(nouveau)* : Le système doit notifier via Mattermost l'ensemble des Managers au démarrage et à la fin d'une période de délégation active, afin qu'ils sachent à qui s'adresser pour une demande urgente.

### 2.2 Tableau de bord (EF-DASH)

- **EF-DASH-01** : Le système doit afficher à l'Admin, sur sa page d'accueil, un tableau de bord synthétique comportant : nombre d'employés actifs (total et par département), nombre de demandes administratives en attente d'approbation, nombre d'anomalies de pointage non résolues du jour, nombre de candidatures en attente de traitement (toutes étapes confondues), nombre de stagiaires dont la fin de contrat est dans les 7 jours.
- **EF-DASH-02** : Le Manager doit disposer d'une vue d'accueil réduite à son périmètre : nombre d'employés dans son département, demandes administratives en attente pour son équipe, anomalies de pointage de son équipe du jour.
- **EF-DASH-03** : Chaque compteur du tableau de bord doit être cliquable et rediriger vers la liste filtrée correspondante.
- **EF-DASH-04** : Les données du tableau de bord doivent être rafraîchies à chaque chargement de page.
- **EF-DASH-05** *(nouveau)* : Lorsqu'une délégation d'approbation est active (cf. EF-AUTH-11), le tableau de bord Admin doit afficher un bandeau indiquant l'identité du délégué actif et la période de validité, afin que l'Admin principal (et tout autre Admin secondaire) voie en permanence qui exerce actuellement les droits d'approbation.

### 2.3 Dossier Employé (EF-EMP)

- **EF-EMP-01** : Le système doit permettre la création d'une fiche employé comprenant : nom, prénom, contact, poste, département, date d'embauche, type de contrat (CDI / CDD / Stagiaire / Stagiaire rémunéré), manager rattaché.
- **EF-EMP-02** : Le système doit permettre la modification et la désactivation (départ) d'une fiche employé, sans suppression physique des données.
- **EF-EMP-03** : Le système doit permettre de rattacher des documents à une fiche employé (contrat, pièce d'identité, etc.).
- **EF-EMP-04** : Le système doit permettre de consulter la liste des employés avec filtres (département, manager, type de contrat, statut actif/inactif) et une **recherche texte libre** *(complété)* par nom, prénom ou e-mail, combinable avec les filtres ci-dessus.
- **EF-EMP-05** : Le système doit permettre, lors de l'embauche d'un candidat recruté (statut → "Embauché"), de générer automatiquement une fiche employé pré-remplie à partir des données extraites par l'IA lors de l'analyse du CV (nom, prénom, e-mail, téléphone, intitulé de poste si détecté). Les champs non extractibles du CV (département, type de contrat, date d'embauche, manager rattaché) sont laissés vides pour complétion manuelle par l'Admin RH. L'Admin doit toujours pouvoir réviser les données pré-remplies avant confirmation.
- **EF-EMP-06** : Le système doit représenter la hiérarchie organisationnelle (département → manager → employés) sur un modèle **un employé / un seul manager direct** (rattachement non matriciel, confirmé).
- **EF-EMP-07** : Le système doit permettre l'import en masse de données via fichier Excel ou CSV, couvrant les fiches employés, l'historique de présence, et les soldes de congés existants, ou toute autre donnée que l'Admin RH souhaite migrer, avec rapport d'erreurs sur les lignes invalides.
- **EF-EMP-08** *(nouveau)* : Le système doit bloquer la désactivation d'un Manager tant que des employés actifs lui sont rattachés, en affichant la liste des employés à réaffecter au préalable. Si un Manager n'a aucun employé actif rattaché, sa désactivation est autorisée directement, sans vérification supplémentaire.
- **EF-EMP-09** *(nouveau)* : Le système doit générer une carte employé par employé, contenant : photo (optionnelle), nom et prénom, poste, département, et QR code actif. La carte doit être disponible en deux formats : (a) **version numérique** (image affichable sur téléphone ou écran, utilisable au kiosque de pointage) ; (b) **version imprimable** (PDF au format carte standard 85×54mm, à imprimer et plastifier par l'Admin RH). La carte est générée une seule fois à la création de la fiche employé. L'Admin RH peut déclencher manuellement une regénération depuis la fiche employé (ex. changement de photo, correction d'informations). Si le QR code est bloqué (cf. EF-ATT-01), la carte numérique associée cesse d'être fonctionnelle au scan.
- **EF-EMP-10** *(nouveau)* : Le système doit permettre à l'Admin de créer, modifier (nom, manager rattaché) et désactiver un département. Un département ne peut être désactivé que s'il ne compte plus aucun employé actif rattaché (analogue à la règle de blocage du Manager, cf. EF-EMP-08) ; les employés à réaffecter ou désactiver au préalable sont listés à l'écran.
- **EF-EMP-11** *(nouveau)* : Le système doit permettre de transférer un employé actif d'un département/manager vers un autre, avec traçabilité du changement (date d'effet, ancien et nouveau rattachement) dans l'historique de la fiche employé. Ce transfert n'affecte pas le solde de congés ni l'historique de présence déjà enregistré.
- **EF-EMP-12** *(nouveau)* : Le système doit permettre une recherche texte libre par nom, prénom ou e-mail sur la liste des employés, combinable avec les filtres existants (cf. EF-EMP-04).
- **EF-EMP-13** *(nouveau)* : À la création d'une fiche employé (manuelle, cf. EF-EMP-01, ou automatique depuis une embauche, cf. EF-EMP-05), le système doit notifier le Manager du département rattaché via Mattermost (webhook), à titre d'information, sur le modèle des notifications déjà prévues pour le recrutement et les demandes administratives (cf. EF-REC-08, EF-ADM-08).
- **EF-EMP-14** *(nouveau)* : Le système doit permettre à l'Admin RH de générer les cartes employé (cf. EF-EMP-09) en lot pour un groupe d'employés sélectionnés (ex. tous les employés sans carte, ou un import récent via EF-EMP-07), produisant un fichier PDF unique regroupant l'ensemble des cartes imprimables au format 85×54mm, prêt à découper.
- **EF-EMP-15** *(nouveau — suivi CDD)* : Le système doit permettre de renseigner, pour tout employé de type CDD, une **date de fin de contrat prévue** (champ optionnel — un CDD sans terme connu à la création reste possible, à compléter ultérieurement). Ce champ est absent/non applicable pour les types CDI, Stagiaire et Stagiaire rémunéré.
- **EF-EMP-16** *(nouveau)* : La date de fin de contrat prévue (EF-EMP-15) est distincte de la **date de départ effective** saisie à la désactivation (cf. EF-DOC-08) — un CDD peut être renouvelé (date repoussée), rompu de manière anticipée, ou requalifié en CDI (date supprimée) avant son terme initial. Ces deux dates coexistent dans la fiche employé et ne sont jamais confondues.

### 2.4 Présence (EF-ATT)

- **EF-ATT-01** : Le système doit générer un identifiant scannable unique (QR code) par employé, **une seule fois à la création de la fiche employé**. Le QR code est statique (pas de rotation) et reste valide jusqu'à ce qu'il soit explicitement bloqué par l'Admin RH. L'Admin RH doit pouvoir bloquer un QR code depuis la fiche employé (cas de perte de carte, départ non planifié, ou abus constaté) ; un QR code bloqué est rejeté à tout scan ultérieur.
- **EF-ATT-02** : Le système doit permettre l'enregistrement d'une entrée (check-in) et d'une sortie (check-out) via scan, horodatées. **Deux scans par jour uniquement** (arrivée matin, départ soir) ; aucun pointage n'est effectué pour la pause de midi.
- **EF-ATT-03** : Le système doit calculer automatiquement le temps de présence journalier à partir des paires entrée/sortie, en déduisant systématiquement 1 heure de pause fixe (`temps_présence = (check-out − check-in) − 1h`).
- **EF-ATT-04** : Le système doit signaler les anomalies de pointage suivantes, évaluées par rapport à l'horaire de référence en vigueur (cf. EF-ATT-07) :
  - **Retard** : check-in effectué après l'heure de début + tolérance (08h40 par défaut).
  - **Départ anticipé** : check-out effectué avant l'heure de fin − tolérance (16h50 par défaut).
  - **Absence de check-out** : check-in sans check-out correspondant en fin de journée.
  - **Présence incomplète** : paire entrée/sortie invalide (ex. deux check-in consécutifs sans check-out entre eux).
- **EF-ATT-05** : Le système doit permettre à un Admin/Manager de consulter l'historique de présence d'un employé ou d'une équipe sur une période donnée.
- **EF-ATT-06** : Le système doit permettre la correction manuelle d'un pointage par un Admin (cas d'oubli de scan), avec traçabilité de la modification.
- **EF-ATT-07** *(nouveau)* : Le système doit maintenir un horaire de référence entreprise configurable par l'Admin RH, comprenant : heure de début et fin du matin, heure de début et fin de l'après-midi, tolérance de retard/départ anticipé en minutes. Horaire initial : **08h30–13h00 / 14h00–17h00, tolérance 10 minutes**. Toute modification de cet horaire est historisée (date d'effet) et ne s'applique qu'aux pointages **futurs** ; les pointages passés restent évalués selon l'horaire en vigueur au moment du scan.

### 2.5 Recrutement (EF-REC)

- **EF-REC-01** : Le système doit permettre la création d'une offre d'emploi (intitulé, description, département cible, statut ouvert/fermé).
- **EF-REC-02** : Le système doit ingérer automatiquement les candidatures reçues par e-mail (boîte de réception surveillée par le workflow n8n), en extrayant le CV et les informations de contact du candidat.
- **EF-REC-03** : Le système doit normaliser les candidatures provenant de différentes sources (Indeed, LinkedIn, candidature directe) dans une structure de données commune.
- **EF-REC-04** : Le système doit déclencher automatiquement une analyse IA de chaque CV ingéré, produisant : (a) **données personnelles extraites** : prénom, nom, e-mail, téléphone, intitulé de poste — réutilisées pour pré-remplir la fiche employé en cas d'embauche (cf. EF-EMP-05) ; (b) **analyse de correspondance** : compétences extraites, années d'expérience estimées, score de correspondance avec l'offre, justification synthétique du score, et une **liste de mots-clés structurée** (compétences, technologies) réutilisable pour un matching ultérieur (cf. EF-REC-12).
- **EF-REC-05** : Si l'analyse IA échoue ou est indisponible, le système doit conserver la candidature avec un statut "analyse en attente", consultable et traitable manuellement, sans bloquer le pipeline de recrutement.
- **EF-REC-06** : Le système doit permettre à l'Admin de consulter, trier et filtrer les candidatures par score IA, offre, statut, ainsi que par **recherche texte libre** *(complété)* sur le nom ou l'e-mail du candidat.
- **EF-REC-07** : Le système doit modéliser un pipeline de recrutement en étapes définies : **Reçu → Présélectionné → Entretien → Décision → Embauché / Rejeté**, avec un statut transversal **"En attente"** (offre fermée à la réception, cf. EF-REC-11) et **"Archivée"** (retenue au-delà de la fenêtre de rétention, cf. EF-REC-12).
- **EF-REC-08** : Lorsqu'une candidature est passée à l'étape "Entretien", le système doit notifier automatiquement le Manager du département concerné via Mattermost (webhook) et rendre la fiche candidat accessible à ce Manager pour saisie du résultat d'entretien.
- **EF-REC-09** : Le Manager doit pouvoir saisir le résultat de l'entretien (favorable / défavorable + commentaire) depuis son espace, ce résultat étant visible par l'Admin pour la décision finale.
- **EF-REC-10** : Le système doit permettre de relancer manuellement l'analyse IA sur une candidature donnée.
- **EF-REC-11** *(nouveau)* : Une candidature reçue pour une offre fermée doit être conservée avec le statut **"En attente"** plutôt que rejetée, afin de pouvoir être réintégrée automatiquement dans le pipeline si une offre compatible s'ouvre ultérieurement (cf. EF-REC-12).
- **EF-REC-12** *(nouveau)* : À la création d'une nouvelle offre, le système doit comparer ses mots-clés requis à ceux des candidatures en statut "En attente" (limitées à une fenêtre de rétention configurable, ex. 6 mois). Les candidatures dont le score de correspondance dépasse un seuil configurable passent en statut **"Suggestion de réactivation"**, visible par l'Admin RH avec les mots-clés communs affichés ; l'Admin valide manuellement la réintégration au pipeline (statut → "Reçu"). Les candidatures dépassant la fenêtre de rétention sans réactivation passent automatiquement en statut "Archivée" (pas de suppression physique, cf. NFR-DATA-01).
- **EF-REC-13** : Lorsqu'un candidat atteint le statut "Embauché", le système doit déclencher la création de sa fiche employé (cf. EF-EMP-05).
- **EF-REC-14** *(nouveau)* : Lorsqu'une candidature passe au statut "Rejeté", le système doit envoyer automatiquement un e-mail de notification au candidat, avec un corps de message standard (éditable par l'Admin avant envoi). L'envoi est journalisé (date, destinataire) de la même manière qu'un envoi de document RH (cf. EF-DOC-06).

### 2.6 Demandes Administratives (EF-ADM)

Module généralisé couvrant les congés (journée complète ou demi-journée), bons de sortie, et l'envoi de documents libres à un employé. Toutes les demandes sont gérées et approuvées exclusivement par l'Admin RH.

- **EF-ADM-01** : Le système doit permettre l'enregistrement d'une demande administrative pour un employé par l'Admin, avec : type de demande (congé / bon de sortie / document libre / autre), granularité (journée complète, demi-journée matin, demi-journée après-midi pour les congés), dates ou créneau horaire selon le type, motif. **Lors de la création d'une demande de congé, le système doit afficher le solde disponible de l'employé et empêcher la sélection d'une durée supérieure à ce solde** (contrôle à la saisie).
- **EF-ADM-02** : Le système doit permettre l'approbation ou le rejet d'une demande par l'Admin RH uniquement. Les Managers n'ont pas de droit d'approbation.
- **EF-ADM-03** : Pour les demandes de type congé, le système doit calculer le solde de jours de congés par employé via un **registre de mouvements** (ledger) plutôt qu'un champ stocké mutable : chaque approbation/annulation génère un mouvement (−0,5j/−1j à l'approbation, +0,5j/+1j à l'annulation) ; le solde à un instant T est la somme des mouvements jusqu'à cette date. L'accumulation mensuelle (1,5 jour/mois pour CDI/CDD, 0 pour stagiaires) est calculée à la volée à partir de la date d'embauche, sans job planifié ni ligne stockée dédiée.
- **EF-ADM-04** : Pour les demandes de type bon de sortie, le système doit enregistrer une plage horaire (heure de départ / heure de retour prévue) et un motif. Les bons de sortie n'affectent pas le solde de congés.
- **EF-ADM-05** : Le système doit valider côté serveur, à l'enregistrement de toute demande de congé, que la durée ne dépasse pas le solde disponible de l'employé — en complément du contrôle déjà appliqué à la saisie (EF-ADM-01), pour se protéger d'un contournement via appel direct à l'API (cf. NFR-SEC-05).
- **EF-ADM-06** : Le système doit permettre de consulter l'historique des demandes administratives d'un employé ou d'une équipe, filtrable par type, statut et période.
- **EF-ADM-07** : Le système doit permettre l'ajout de nouveaux types de demande administrative sans modification structurelle (extensibilité du type de requête).
- **EF-ADM-08** : Lors de l'approbation ou du rejet d'une demande, le système doit envoyer une notification via Mattermost (webhook) à destination du Manager du département concerné, à titre d'information.
- **EF-ADM-09** *(nouveau)* : Le système doit permettre à l'Admin RH de créer une demande administrative de type "document libre", consistant à uploader un fichier arbitraire (attestation de travail, autorisation spéciale, etc.) et à l'envoyer par e-mail à un employé. Cet envoi doit être journalisé de la même manière qu'un envoi de certificat de stage (date d'envoi, envoyé par, destinataire — cf. EF-DOC-06).
- **EF-ADM-10** *(nouveau)* : Le système doit maintenir un calendrier des jours fériés (Maroc), géré manuellement par l'Admin RH (ajout, modification, suppression d'une date fériée), utilisé dans le calcul du décompte des jours de congé consommés (un jour férié inclus dans une période de congé approuvée n'est pas décompté du solde). **Note de conception** : pas de dépendance à une API externe de jours fériés — la couverture du Maroc y est incomplète, et les fêtes du calendrier hégirien (Aïd al-Fitr, Aïd al-Adha, Achoura, Mouloud) sont mobiles et confirmées officiellement seulement quelques jours à l'avance ; leur saisie reste donc manuelle et sous contrôle de l'Admin RH. Les jours fériés civils à date fixe peuvent être pré-remplis à l'installation à titre de valeur par défaut, modifiable.

### 2.7 Documents RH (EF-DOC)

Ce module gère la génération automatique de documents RH à destination des employés (certificat de stage, certificat de travail), et, de façon généralisée, l'envoi de tout document libre à un employé (cf. EF-ADM-09).

- **Certificat de stage** (`certificat_de_stage`) : document numérique généré automatiquement par le système à partir des données de la fiche employé, envoyé par e-mail au stagiaire à la fin de son stage.
- **Attestation de stage** : document physique délivré manuellement par l'entreprise. Le système ne le génère pas — il notifie uniquement le stagiaire par e-mail de se présenter au bureau pour le récupérer.
- **Certificat de travail** (`certificat_de_travail`) *(nouveau)* : document numérique obligatoire (droit marocain) généré automatiquement à partir des données de la fiche employé, pour tout employé de type CDI ou CDD quittant l'entreprise. Contrairement au certificat de stage, il ne concerne pas les Stagiaires/Stagiaires rémunérés, n'est déclenché par aucune surveillance planifiée (la date de départ est saisie manuellement par l'Admin, pas prévisible à l'avance comme une fin de stage), et ne calcule ni n'affiche aucune indemnité financière (paie hors périmètre, cf. §4) — seul le solde de congés restant non pris est affiché à titre informatif.

- **EF-DOC-01** : Le système doit surveiller automatiquement les dates de fin de contrat des employés de type Stagiaire et Stagiaire rémunéré via un workflow planifié (n8n, déclenchement quotidien).
- **EF-DOC-02** *(précisé)* : Le système doit envoyer une notification à l'Admin RH via Mattermost **3 jours ouvrables avant** la date de fin de stage, avec le nom du stagiaire, la date de fin, et un lien d'action vers le formulaire de confirmation d'envoi. Cette notification est **unique** — le système n'envoie pas de relance automatique. Si l'Admin RH ne confirme pas l'envoi, aucune action système supplémentaire n'est déclenchée ; le rattrapage repose sur l'initiative du stagiaire lui-même (rappel par e-mail à l'Admin RH, ou passage physique au bureau pour récupérer son attestation le jour venu). L'action de confirmation reste disponible manuellement depuis la fiche employé à tout moment (cf. EF-DOC-07).
- **EF-DOC-03** : L'Admin RH doit pouvoir confirmer l'envoi du certificat de stage depuis l'interface, après avoir éventuellement révisé les données pré-remplies.
- **EF-DOC-04** : À confirmation, le système doit générer automatiquement un certificat de stage au format PDF, pré-rempli avec les données de la fiche employé.
- **EF-DOC-05** : Le système doit envoyer un e-mail au stagiaire contenant le certificat en pièce jointe et un corps de message pré-rempli (éditable par l'Admin) l'informant de se présenter au bureau pour son attestation physique.
- **EF-DOC-06** : L'envoi du certificat (ou de tout document libre, cf. EF-ADM-09) doit être journalisé dans la fiche employé (date d'envoi, envoyé par, adresse de destination), pour traçabilité et audit.
- **EF-DOC-07** : Le système doit permettre le renvoi manuel du certificat de stage depuis la fiche employé, avec traçabilité de chaque envoi.
- **EF-DOC-08** *(nouveau)* : Lors de la désactivation d'un employé de type CDI ou CDD (cf. EF-EMP-02), le système doit demander à l'Admin RH de saisir une **date de départ** et un **motif** (démission, licenciement, fin de CDD, rupture, autre) avant de confirmer la désactivation.
- **EF-DOC-09** *(nouveau)* : À la saisie du départ, le système doit afficher à l'Admin RH le solde de congés restant non pris de l'employé (calculé via le registre, cf. EF-ADM-03), à titre informatif uniquement — aucune indemnisation ni calcul financier n'est effectué par le système (paie hors périmètre, cf. §4).
- **EF-DOC-10** *(nouveau)* : Le système doit permettre à l'Admin RH de générer un certificat de travail au format PDF, pré-rempli avec les données de la fiche employé (poste, dates d'emploi), déclenché manuellement depuis la fiche employé au moment du départ (pas de surveillance planifiée, contrairement au certificat de stage — cf. EF-DOC-01).
- **EF-DOC-11** *(nouveau)* : Le système doit envoyer le certificat de travail par e-mail à l'employé sortant, avec un corps de message éditable par l'Admin, et journaliser cet envoi de la même manière qu'un certificat de stage (date, envoyé par, destinataire — cf. EF-DOC-06). Le renvoi manuel ultérieur doit rester possible, avec traçabilité (analogue à EF-DOC-07).
- **EF-DOC-12** *(nouveau — surveillance CDD)* : Le système doit surveiller automatiquement les dates de fin de contrat prévues (EF-EMP-15) des employés de type CDD, sur le même principe que la surveillance des fins de stage (EF-DOC-01), via le workflow n8n planifié existant (déclenchement quotidien, extension du même job plutôt que duplication).
- **EF-DOC-13** *(nouveau)* : Le système doit notifier l'Admin RH via Mattermost **15 jours ouvrables avant** la date de fin de contrat prévue d'un CDD (délai configurable indépendamment de celui du stage — cf. EF-DOC-02 — car l'offboarding d'un CDD implique davantage de démarches administratives qu'une simple remise d'attestation), avec le nom de l'employé, la date de fin, et un lien d'action vers la fiche employé pour préparer le départ (saisie de date/motif EF-DOC-08, génération du certificat de travail EF-DOC-10).
- **EF-DOC-14** *(nouveau)* : Contrairement à la notification de fin de stage (EF-DOC-02, non relancée), cette notification doit être **répétée une seconde fois à J-3** si aucune action de désactivation n'a été engagée sur la fiche employé entre-temps — le départ d'un CDI/CDD engageant des obligations légales plus lourdes qu'un stage justifie cette relance unique supplémentaire.
- **EF-DOC-15** *(nouveau)* : Si la date de fin de contrat prévue est modifiée (renouvellement), supprimée (promotion CDI, cf. EF-DOC-16), ou si l'employé est désactivé avant l'échéance, toute notification planifiée et non encore envoyée doit être annulée ou recalculée — même principe que pour le stage (cf. §3.5).
- **EF-DOC-16** *(nouveau — règle métier)* : Un changement de type de contrat CDD → CDI (cf. §1.1, ajustement manuel du solde de congés déjà prévu) doit automatiquement vider le champ date de fin de contrat prévue (EF-EMP-15) et annuler toute surveillance associée. Ce changement de type reste une action manuelle de l'Admin RH ; le système ne le déduit jamais automatiquement.

### 2.8 Export & Reporting (EF-EXP) *(nouveau)*

Module transverse permettant l'extraction des données consultées à l'écran vers des formats exploitables hors système (nécessaire à un usage RH réel : archivage, transmission à la comptabilité, contrôle interne).

- **EF-EXP-01** *(nouveau)* : Le système doit permettre à l'Admin d'exporter la liste des employés (filtrée selon les critères de EF-EMP-04) au format Excel (.xlsx) ou PDF.
- **EF-EXP-02** *(nouveau)* : Le système doit permettre à l'Admin ou au Manager (limité à son périmètre) d'exporter l'historique de présence d'un employé ou d'une équipe sur une période donnée (feuille de présence mensuelle), au format Excel ou PDF.
- **EF-EXP-03** *(nouveau)* : Le système doit permettre à l'Admin d'exporter l'historique des demandes administratives (filtré selon les critères de EF-ADM-06) au format Excel ou PDF.
- **EF-EXP-04** *(nouveau)* : Chaque export doit être généré à la demande (pas de job planifié), et refléter les données au moment de la génération.

### 2.9 Configuration & Paramétrage (EF-CFG) *(nouveau)*

Module transverse regroupant, dans un écran unique accessible à l'Admin, l'ensemble des paramètres système déjà introduits séparément dans les modules ci-dessus, ainsi que l'interface de consultation du journal d'audit. Il ne s'agit pas de nouvelles règles métier, mais de la consolidation de leur point d'accès pour la conception de l'interface — cf. EF-AUTH-05, qui mentionne "la configuration système" sans en détailler le contenu.

- **EF-CFG-01** *(nouveau)* : Le système doit exposer, dans un écran de configuration réservé à l'Admin, l'ensemble des paramètres suivants : horaire de référence entreprise et tolérances (cf. EF-ATT-07), calendrier des jours fériés (cf. EF-ADM-10), seuil de score et fenêtre de rétention pour la réactivation de candidatures (cf. EF-REC-12), politique de verrouillage de compte (nombre de tentatives, délai — cf. EF-AUTH-09), politique de complexité de mot de passe (cf. NFR-SEC-08), **délai de notification de fin de stage** (cf. EF-DOC-02) et **délai de notification de fin de CDD** (cf. EF-DOC-13), configurables indépendamment.
- **EF-CFG-02** *(nouveau)* : Toute modification d'un paramètre de configuration doit être journalisée dans le journal d'audit (qui, quoi, quand — cf. NFR-SEC-03), au même titre que les autres actions sensibles.
- **EF-CFG-03** *(nouveau — consultation audit)* : Le système doit exposer à l'Admin une interface dédiée de consultation du journal d'audit (NFR-SEC-03), filtrable par utilisateur ayant effectué l'action, type d'action, module concerné (Employé, Présence, Recrutement, Demande administrative, Configuration, Délégation, Notification), et période.
- **EF-CFG-04** *(nouveau)* : Le journal d'audit doit être consultable via une **recherche texte libre** portant sur l'utilisateur concerné ou l'identifiant de l'entité affectée (ex. nom d'un employé), sur le même principe que les recherches déjà prévues ailleurs (EF-EMP-12, EF-REC-06).
- **EF-CFG-05** *(nouveau)* : Le journal d'audit doit être exportable (Excel ou PDF), en réutilisant le module Export existant (cf. EF-EXP), pour transmission à un contrôle interne ou externe.
- **EF-CFG-06** *(nouveau — règle métier)* : Le journal d'audit est en **lecture seule** : aucune fonctionnalité de modification ou de suppression manuelle d'une entrée n'est exposée, y compris à l'Admin, afin de préserver son intégrité probante. Sa rétention minimale doit couvrir au moins la durée de conservation légale des documents RH au Maroc `[À CONFIRMER — cf. §7 point 3]`.

### 2.10 Notifications internes (EF-NOTIF) *(nouveau)*

Module transverse assurant, pour chaque notification métier déjà définie (EF-REC-08, EF-ADM-08, EF-EMP-13, EF-DOC-02/13, EF-AUTH-15), un **canal de repli garanti** indépendant de la disponibilité de Mattermost, conformément à l'esprit de NFR-OPS-06. Le canal in-app ne remplace pas Mattermost : les deux fonctionnent en parallèle, et l'in-app garantit que la notification atteint son destinataire même si Mattermost est indisponible ou si l'utilisateur ne consulte pas Mattermost au moment où l'événement survient.

- **EF-NOTIF-01** *(nouveau)* : Chaque notification métier envoyée via webhook Mattermost doit générer, en parallèle et de façon systématique, une entrée correspondante dans un flux de notifications in-app propre à chaque destinataire (Admin ou Manager concerné), sans dépendance au succès de l'envoi Mattermost.
- **EF-NOTIF-02** *(nouveau)* : Un badge indiquant le nombre de notifications non lues doit être visible depuis n'importe quelle page de l'application, pour l'utilisateur connecté.
- **EF-NOTIF-03** *(nouveau)* : Chaque notification doit être cliquable et rediriger directement vers l'élément concerné (fiche employé, demande administrative, candidature), sur le même principe que les compteurs du tableau de bord (cf. EF-DASH-03).
- **EF-NOTIF-04** *(nouveau)* : L'utilisateur doit pouvoir marquer une notification comme lue individuellement, ou l'ensemble de ses notifications en masse.
- **EF-NOTIF-05** *(nouveau)* : Les notifications in-app doivent être conservées 90 jours (configurable) puis archivées automatiquement ; contrairement au journal d'audit (cf. EF-CFG-06), elles n'ont pas de valeur probante et peuvent être purgées.
- **EF-NOTIF-06** *(nouveau)* : En cas d'échec de l'envoi du webhook Mattermost (service indisponible), le système doit journaliser l'échec (cf. NFR-OPS-06 déjà en vigueur) **et** garantir que la notification in-app correspondante a bien été créée — ceci constitue le mécanisme concret qui rend l'indisponibilité de Mattermost non bloquante pour l'utilisateur final.

---

## 3. Cas limites et règles métier

### 3.1 Dossier Employé

- Un employé désactivé conserve son historique ; ces données ne sont jamais supprimées, seulement liées à un statut "inactif".
- Un employé ne peut être rattaché qu'à un seul manager à la fois (`manager_id` unique) — **confirmé définitivement**, pas de structure matricielle.
- La désactivation d'un employé doit automatiquement clôturer ou invalider ses demandes administratives en attente.
- **Manager désactivé — règle confirmée** : la désactivation d'un Manager ayant des employés actifs rattachés est **bloquée** jusqu'à réaffectation explicite de chacun de ces employés à un autre Manager. Un Manager sans employé actif rattaché peut être désactivé directement, sans vérification supplémentaire (cf. EF-EMP-08). Corollaire : chaque département conserve toujours au moins un Manager actif tant qu'il compte des employés actifs.

### 3.2 Présence

- Un scan d'entrée sans scan de sortie correspondant en fin de journée doit être marqué comme anomalie ("présence incomplète").
- Deux scans d'entrée consécutifs sans sortie entre les deux doivent être rejetés ou signalés.
- Le fuseau horaire et l'horodatage doivent être cohérents indépendamment du poste utilisé pour le scan (le serveur fait foi).
- Un scan effectué pour un employé désactivé doit être rejeté.
- **Horaire de référence — règle confirmée** : horaire unique pour toute l'entreprise (pas de variation par département/employé), 08h30–13h00 / 14h00–17h00, tolérance de 10 minutes au check-in (max 08h40) et au check-out (min 16h50). Aucun pointage pour la pause de midi — la pause de 1h est déduite automatiquement du calcul de présence (cf. EF-ATT-03). L'horaire est modifiable par l'Admin RH et historisé : tout changement ne s'applique qu'aux pointages futurs, les pointages passés restant évalués selon l'horaire en vigueur à leur date.

### 3.3 Recrutement

- Une même adresse e-mail candidate sur la même offre ne doit générer qu'une seule candidature active (déduplication, cf. NFR-DATA-03).
- Un CV dans un format non supporté ou illisible doit être conservé avec un statut "non traité" plutôt que de faire échouer silencieusement l'ingestion entière.
- Un candidat déjà "embauché" via une offre ne peut pas être ré-engagé dans le pipeline d'une autre offre sans action explicite.
- **Offre fermée — règle confirmée** : une candidature reçue pour une offre fermée passe en statut "En attente" (ni rejetée ni perdue). Elle est réévaluée automatiquement par mots-clés à la création de toute nouvelle offre (cf. EF-REC-11/12), avec validation humaine avant réintégration effective au pipeline, et passage en "Archivée" au-delà de la fenêtre de rétention configurée.

### 3.4 Demandes Administratives

- Une demande chevauchant une demande déjà approuvée pour le même employé sur la même période doit être signalée. Pour les demi-journées, le chevauchement est vérifié au niveau matin/après-midi.
- Une demande déjà approuvée puis annulée doit recréditer le solde de congés correspondant (mouvement +0,5j ou +1j dans le registre, cf. EF-ADM-03).
- **Solde insuffisant — règle confirmée** : le système empêche la sélection, dès la saisie de la demande, d'une durée supérieure au solde disponible affiché à l'Admin RH (front-end), avec revalidation stricte côté serveur à l'enregistrement (EF-ADM-05). Aucune requalification automatique en "absence non rémunérée" n'est nécessaire — le contrôle en amont rend ce cas structurellement impossible.
- Une demande de bon de sortie ne doit pas pouvoir être créée pour une date où l'employé est déjà en congé approuvé.
- **Modèle de solde — règle confirmée** : le solde de congés est représenté par un **registre de mouvements** (ledger), pas un champ mutable. Le solde initial démarre à 0 à la date d'embauche et s'accumule à raison de 1,5 jour/mois travaillé (calcul à la volée, sans job planifié). Un changement de type de contrat en cours d'année (ex. stagiaire promu CDI) n'est **pas recalculé automatiquement** par le système ; l'Admin RH effectue les ajustements nécessaires manuellement (ex. mouvement d'ajustement explicite dans le registre).
- **Jours fériés — règle confirmée** *(nouveau)* : un jour férié (cf. EF-ADM-10) tombant à l'intérieur d'une période de congé approuvée n'est pas comptabilisé comme jour de congé consommé ; le mouvement de décompte dans le registre exclut les jours fériés de la période. Si un jour férié est ajouté ou supprimé du calendrier après l'approbation d'un congé qui le chevauche, le solde n'est pas recalculé rétroactivement ; l'Admin RH effectue un ajustement manuel si nécessaire (cohérent avec la règle de non-recalcul automatique ci-dessus).

### 3.5 Documents RH

- Si la date de fin de stage est modifiée après qu'une notification a déjà été envoyée, le système doit annuler ou remettre à jour la notification planifiée.
- Si le stagiaire n'a pas d'adresse e-mail renseignée, le système doit bloquer l'envoi et afficher un avertissement à l'Admin.
- Le certificat de stage ne peut être généré que pour un employé de type Stagiaire ou Stagiaire rémunéré.
- **Relance de notification — règle confirmée** : le système n'envoie **aucune relance automatique** après la notification initiale à J-3 (cf. EF-DOC-02). Le rattrapage en cas d'oubli de l'Admin RH repose sur l'initiative du stagiaire (e-mail de rappel à l'Admin, ou passage physique au bureau pour récupérer son attestation le jour de fin de stage). L'action de confirmation manuelle reste disponible à tout moment depuis la fiche employé.
- **Départ CDI/CDD — règle confirmée** *(nouveau)* : le certificat de travail ne peut être généré que pour un employé de type CDI ou CDD (jamais Stagiaire/Stagiaire rémunéré, qui relève du certificat de stage). Contrairement à la fin de stage, la date de départ n'est ni prévisible ni surveillée automatiquement : elle est saisie manuellement par l'Admin RH au moment de la désactivation (cf. EF-DOC-08), ce qui déclenche le blocage du QR code à cette date (cf. EF-ATT-01) et l'affichage informatif du solde de congés restant (cf. EF-DOC-09), sans aucun calcul d'indemnité.
- **Surveillance CDD — règle confirmée** *(nouveau)* : la surveillance planifiée des fins de CDD (EF-DOC-12) s'appuie sur la date de fin de contrat prévue (EF-EMP-15), qui reste distincte de la date de départ effective (EF-DOC-08). Un CDD renouvelé (date repoussée manuellement par l'Admin) déclenche un recalcul complet des notifications planifiées (annulation des notifications futures obsolètes, planification des nouvelles échéances J-15 et J-3 sur la nouvelle date). Une promotion CDD → CDI (EF-DOC-16) vide la date de fin et annule toute surveillance sans en planifier de nouvelle. Contrairement au stage, le CDD bénéficie d'une **relance unique à J-3** (EF-DOC-14) si aucune action de désactivation n'a été engagée après la notification initiale à J-15 — au-delà, aucune nouvelle relance système, le rattrapage repose sur l'initiative de l'employé ou sur l'action manuelle de l'Admin.

---

## 4. Hypothèses et contraintes

**Hypothèses :**

- L'entreprise est une organisation unique (pas de support multi-société / multi-tenant).
- L'entreprise comporte au moins 3 départements, chacun avec un manager dédié. Structure hiérarchique simple (un manager par employé).
- Interface destinée à un usage en français uniquement.
- Le calcul de la paie et tout élément financier lié au salaire sont hors périmètre.
- Authentification interne à l'application (pas de SSO externe).
- Le matériel de scan (QR) est simulé via navigateur web.
- L'accès aux API Indeed/LinkedIn se fait exclusivement via canal e-mail.
- Le modèle d'IA utilisé est accessible via API externe (Claude/Anthropic recommandé).
- Les notifications internes sont acheminées via Mattermost (webhook).
- Les règles de congés suivent le droit marocain du travail tel que confirmé par l'encadrant (cf. §1.1).

**Contraintes :**

- Délai de conception : une semaine, validation requise avant le passage en phase de réalisation.
- Équipe de deux personnes avec compétences DevOps/backend, stack libre.
- Le projet doit pouvoir être présenté comme livrable de stage et comme pièce de portfolio.
- Dépendance à un service IA externe : toute fonctionnalité reposant sur l'IA doit explicitement prévoir un comportement dégradé en cas d'indisponibilité (cf. NFR-PERF-04).
- Dépendance à n8n comme couche d'orchestration : son indisponibilité ne doit pas empêcher l'usage du reste de l'application `[à formaliser en exigence si retenu — cf. §6, point d ouvert]`.

---

## 5. Priorisation MoSCoW (Must , Should , Could , Won't )

### Must (socle indispensable)

- EF-AUTH-01, 02, 05 — connexion et contrôle d'accès de base
- EF-EMP-01, 02, 04 — création, modification, consultation des fiches employé
- EF-ATT-01, 02, 03, 07 — génération QR, scan entrée/sortie, calcul du temps de présence, horaire de référence configurable
- EF-REC-01, 02, 06, 07 — création d'offre, ingestion de candidature, consultation/tri, progression dans le pipeline
- EF-ADM-01, 02, 03, 05 — création de demande, approbation/rejet, solde de congés (registre), contrôle strict du solde
- EF-EMP-08 — blocage de désactivation Manager avec employés actifs
- NFR-SEC-01, 02, 04, 05 — chiffrement, mots de passe hachés, auth par jeton, RBAC serveur
- NFR-SEC-07 — validation et scan antivirus des fichiers uploadés
- NFR-SEC-08, 09 — complexité de mot de passe, rate limiting API

### Should (forte valeur, non bloquant)

- EF-AUTH-03, 04, 06, 07 — scoping Manager, journal de connexion, gestion des comptes
- EF-AUTH-08, 09, 10 — réinitialisation de mot de passe, verrouillage de compte, gestion de session
- EF-AUTH-11, 12, 13, 14, 15 — délégation temporaire d'approbation (Admin ou Manager élevé), traçabilité, notification Managers
- EF-EMP-03, 05, 07 — pièces jointes, création auto depuis candidat embauché (avec pré-remplissage IA), import Excel étendu
- EF-EMP-09 — carte employé numérique + imprimable (photo, QR, poste, département)
- EF-EMP-10, 11 — CRUD département, transfert d'employé entre départements/managers
- EF-EMP-12, 13 — recherche texte libre employés, notification Manager à l'embauche
- EF-EMP-15, 16 — date de fin de contrat prévue pour les CDD, distinguée de la date de départ effective
- EF-ATT-04, 05 — détection d'anomalies (retard, départ anticipé, incomplet), consultation d'historique
- EF-REC-03, 04, 05, 08, 09, 11, 12, 13, 14 — normalisation multi-source, analyse IA + mots-clés, dégradation gracieuse, réactivation "En attente", auto-création de fiche employé, notification de rejet candidat
- EF-ADM-04, 06, 09, 10 — bons de sortie, historique filtrable, envoi de document libre, calendrier des jours fériés
- EF-DASH-05 — bandeau de délégation active sur le tableau de bord Admin
- EF-DOC-01, 02, 03, 04, 05, 06 — flux complet Documents RH (sans relance automatique)
- EF-DOC-08, 09, 10, 11 — workflow de départ CDI/CDD, certificat de travail
- EF-DOC-12, 13, 14, 15, 16 — surveillance CDD, notification à J-15 + relance à J-3, annulation en cas de renouvellement ou promotion CDI
- EF-EXP-01, 02, 03, 04 — export Excel/PDF des listes, présences et demandes
- EF-CFG-01, 02 — écran de configuration centralisé, audit des changements de paramètres
- EF-CFG-03, 04, 05, 06 — consultation, filtrage, recherche et export du journal d'audit, en lecture seule
- EF-NOTIF-01, 02, 03, 04, 05, 06 — centre de notifications in-app comme canal de repli garanti indépendant de Mattermost
- NFR-SEC-03, 06 — journal d'audit complet, gestion des secrets
- NFR-OPS-01, 02 — conteneurisation, CI de base

### Could (agréable, sacrifiable si le temps manque)

- EF-EMP-06 — représentation hiérarchique multi-niveaux avancée
- EF-ATT-06 — correction manuelle de pointage avec traçabilité
- EF-ADM-07 — extensibilité formelle du type de requête
- EF-DOC-07 — renvoi manuel du certificat depuis la fiche employé
- EF-EMP-14 — génération de cartes employé en lot
- NFR-OPS-03, 04, 05 — logs structurés avancés, n8n versionné/exportable
- NFR-UX-02 — mode kiosque dédié pour le scan
- EF-DASH-01, 02, 03, 04 — tableau de bord compteurs/alertes

### Won't (hors périmètre)

- Authentification/self-service employé
- Paie / calcul des salaires
- Intégration matérielle biométrique
- Intégration API directe Indeed/LinkedIn
- Module Performance/évaluations
- Support multi-entreprise / multi-tenant
- Application mobile native

---

## 6. Exigences non fonctionnelles

### 6.1 Sécurité (NFR-SEC)

- **NFR-SEC-01** : Les données personnelles (PII) doivent être chiffrées au repos et en transit (TLS).
- **NFR-SEC-02** : Les mots de passe doivent être stockés sous forme hachée (bcrypt/argon2).
- **NFR-SEC-03** : Toute action sensible doit être journalisée dans un journal d'audit horodaté et attribué à l'utilisateur.
- **NFR-SEC-04** : L'accès à l'API doit être protégé par authentification par jeton (JWT ou équivalent), avec expiration.
- **NFR-SEC-05** : Le contrôle d'accès basé sur les rôles doit être appliqué côté serveur, sur chaque endpoint — y compris pour les contrôles de solde de congés (cf. EF-ADM-05), pas uniquement côté interface.
- **NFR-SEC-06** : Les clés d'API externes doivent être stockées en variables d'environnement / secrets manager.
- **NFR-SEC-07** *(nouveau)* : Tout fichier uploadé par un utilisateur (CV, pièce jointe de fiche employé, document libre — cf. EF-EMP-03, EF-ADM-09) doit être validé côté serveur sur son type MIME et sa taille (limite configurable, ex. 10 Mo), et soumis à une analyse antivirus avant stockage définitif. Un fichier rejeté par ces contrôles doit être signalé explicitement à l'utilisateur avec le motif du rejet.
- **NFR-SEC-08** *(nouveau)* : Le système doit imposer une politique de complexité du mot de passe à la création et à la modification (longueur minimale de 10 caractères, combinaison de majuscules, minuscules et chiffres au minimum), rejetant côté serveur tout mot de passe non conforme.
- **NFR-SEC-09** *(nouveau)* : Le système doit appliquer une limitation du nombre de requêtes par utilisateur/IP (rate limiting) sur l'ensemble des endpoints de l'API, et pas uniquement sur le formulaire de connexion (cf. EF-AUTH-09), afin de se prémunir contre les abus par appel direct (ex. scan répété, énumération de données).

### 6.2 Performance & disponibilité (NFR-PERF)

- **NFR-PERF-01** : Les opérations de consultation courantes doivent répondre en moins de 1 seconde sous charge nominale.
- **NFR-PERF-02** : L'enregistrement d'un pointage doit être confirmé en moins de 2 secondes.
- **NFR-PERF-03** : L'analyse IA d'un CV est asynchrone et ne doit pas bloquer l'ingestion ou l'affichage de la candidature.
- **NFR-PERF-04** : Le système doit rester utilisable en cas d'indisponibilité temporaire du service IA externe (dégradation gracieuse, cf. EF-REC-05 et EF-REC-12).

### 6.3 Maintenabilité & DevOps (NFR-OPS)

- **NFR-OPS-01** : L'application doit être conteneurisée (Docker), déploiement reproductible.
- **NFR-OPS-02** : Le pipeline CI doit exécuter automatiquement les tests et le linting.
- **NFR-OPS-03** : Le système doit exposer des journaux applicatifs structurés.
- **NFR-OPS-04** : La configuration sensible doit être externalisée de l'image/du code source.
- **NFR-OPS-05** : Le workflow n8n doit être versionné/exportable `[À CONFIRMER — point d, traité ultérieurement]`.
- **NFR-OPS-06** : Les notifications Mattermost doivent être acheminées via webhook configuré en variable d'environnement. L'indisponibilité de Mattermost ne doit pas bloquer les opérations métier.
- **NFR-OPS-07** : L'envoi d'e-mails sortants doit être configuré via un service SMTP externalisé, délégué à n8n. En cas d'échec, l'erreur doit être journalisée et l'Admin notifié via Mattermost.

### 6.4 Utilisabilité (NFR-UX)

- **NFR-UX-01** : L'interface Admin et Manager doit être utilisable sur poste de bureau standard.
- **NFR-UX-02** : Modèle d'accès du kiosque de pointage (réseau local sans authentification vs PIN partagé) `[À CONFIRMER — point e, traité ultérieurement]`.
- **NFR-UX-03** : Les messages d'erreur doivent être explicites et non techniques pour l'utilisateur final (ex. blocage de saisie de congé au-delà du solde, blocage de désactivation Manager).

### 6.5 Fiabilité des données (NFR-DATA)

- **NFR-DATA-01** : Aucune suppression physique des données employé/candidat ; les suppressions sont logiques (statut inactif/archivé).
- **NFR-DATA-02** : Les imports de CV ingérés automatiquement doivent être traçables jusqu'à leur source.
- **NFR-DATA-03** : Le système doit éviter la duplication d'un même candidat ayant postulé plusieurs fois (déduplication par e-mail à minima).

---

## 7. Questions pour l'encadrant — statut

### Résolus (verrouillés)

1. ~~Chefs de département approuvant les congés ?~~ → **Résolu** : approbation centralisée Admin RH uniquement (avec délégation temporaire possible, cf. EF-AUTH-11).
2. ~~Recrutement géré par qui ?~~ → **Résolu** : RH gère réception/sélection, Manager conduit les entretiens.
3. ~~Bon de sortie digitalisé ?~~ → **Résolu** : inclus dans Demandes Administratives.
4. ~~Règle de calcul des congés ?~~ → **Résolu** : droit marocain, registre de mouvements, solde démarrant à 0.
5. ~~Rôle "Chef de Projet" ?~~ → **Résolu** : supprimé, entièrement couvert par le rôle Manager.
6. ~~Autres types de demandes administratives ?~~ → **Résolu** : généralisé à l'envoi de document libre (EF-ADM-09).
7. ~~Import Excel : périmètre ?~~ → **Résolu** : employés + présences + soldes + tout ce que RH souhaite migrer.
8. ~~Délai de notification fin de stage ?~~ → **Résolu** : 3 jours ouvrables avant, notification unique sans relance automatique.

### Ouverts (à trancher avant la réalisation)

1. **Modèle d'accès du kiosque de pointage** (NFR-UX-02) — trois options : réseau local sans authentification, PIN partagé sur le poste kiosque, ou jeton par périphérique. Le choix impacte la conception de l'endpoint de scan et le modèle de déploiement (nombre de kiosques, isolation réseau). Sans impact bloquant sur les diagrammes UML actuels ; à figer avant l'implémentation du module Présence.
2. **Versionnement et export des workflows n8n** (NFR-OPS-05) — modalités à définir : dépôt Git dédié, export périodique automatisé, ou gestion manuelle documentée. Aucun impact structurel sur les diagrammes, purement opérationnel — à figer avant la mise en production.
3. **Durée de rétention légale minimale du journal d'audit au Maroc** (EF-CFG-06) — impacte le dimensionnement du stockage et la politique de purge. À valider avec un contact juridique ou l'encadrant ; le système est conçu pour supporter une durée configurable, la valeur retenue reste à trancher.
4. **Périmètre de la délégation Manager en cas de conflit d'intérêt** (EF-AUTH-11) — quand un Manager reçoit une délégation temporaire de droits d'approbation, doit-il être exclu des demandes concernant son propre département (conflit d'intérêt potentiel), ou cette restriction est-elle disproportionnée pour une équipe de cette taille ? Le système peut implémenter les deux comportements ; le choix est un choix de politique, pas de faisabilité technique.

---

*Document évolutif — sert de base aux diagrammes UML (cas d'utilisation, classes, séquence), au schéma de base de données, et aux décisions d'architecture qui suivent. Version consolidée intégrant l'addendum initial : suivi de fin de contrat CDD (EF-EMP-15/16, EF-DOC-12→16), délégation temporaire d'approbation (EF-AUTH-11→15, EF-DASH-05), centre de notifications in-app (EF-NOTIF-01→06), consultation du journal d'audit (EF-CFG-03→06). Les 4 points ouverts ci-dessus sont à trancher avant la phase de réalisation.*
