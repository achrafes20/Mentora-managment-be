# Prompt Figma — Module RH "Gestion Entreprise" (HB Développement)

*À copier-coller dans Figma (First Draft / Figma AI) ou à transmettre à un designer.*

---

## 1. Contexte produit

Tu conçois l'interface d'un **outil interne RH** pour HB Développement. C'est un outil de travail quotidien, pas un produit grand public — utilisé par deux profils :

- **Admin RH (Amal Medah)** : utilisatrice unique avec accès complet. Elle traite des dossiers, approuve des demandes, consulte des tableaux de données toute la journée. Elle a besoin de **densité d'information maîtrisée** et de **rapidité d'action**, pas d'un habillage marketing.
- **Manager de département** : accès en lecture restreint à son équipe, plus la saisie de résultats d'entretien. Vue allégée, occasionnelle.

Usage : **desktop uniquement**, interface **en français**, thème **clair (jamais sombre)**. Le ton doit être professionnel, sobre et rassurant — c'est un outil qui gère des données personnelles et des décisions administratives sensibles (congés, embauches, départs).

---

## 2. Direction visuelle (à respecter strictement)

### Palette (tokens couleur)

| Rôle | Nom | Hex |
|---|---|---|
| Fond principal | Blanc Papier | `#F7F7F4` |
| Texte / navigation / primaire | Encre Marine | `#1B2A41` |
| Accent positif (approuvé, actif, embauché) | Sauge Administrative | `#4A7C6B` |
| Accent attention (en attente, à traiter) | Ambre Vigilance | `#C87F3A` |
| Accent alerte (anomalie, rejeté, bloqué) | Corail Alerte | `#C1495A` |
| Neutre / bordures / séparateurs | Gris Dossier | `#D8D4CC` |

Pas de dégradés criards, pas de fond crème-terracotta générique, pas de mode sombre. La couleur porte toujours un sens fonctionnel (statut), jamais purement décoratif.

### Typographie

- **Display** (titres de section, gros chiffres du tableau de bord) : une serif structurée à caractère quasi-officiel — *Source Serif 4* ou équivalent. Utilisée avec parcimonie, jamais pour le corps de texte.
- **Corps / interface** : une sans humaniste dense et lisible en tableau — *IBM Plex Sans* ou *Inter*.
- **Utilitaire** (identifiants, horodatages, codes QR, matricules) : une monospace — *IBM Plex Mono*. Elle signale visuellement "ceci est une donnée système", ce qui aide l'Admin RH à scanner vite une fiche.

### Élément signature

Le document de conception introduit un artefact réel : **la carte employé** (EF-EMP-09) — badge avec photo, QR code, poste, département, au format carte 85×54mm. Ce format de carte devient le **motif structurant de toute l'interface** :

- Les cartes statistiques du tableau de bord, les fiches employé résumées dans les listes, et les cartes candidat du pipeline de recrutement reprennent toutes les proportions et le coin caractéristique de la carte badge (un petit repère d'angle rappelant le coin scanné d'un QR code).
- Ce détail d'angle devient un repère visuel cohérent : "ceci représente une personne" partout dans l'outil (employé, candidat, manager).

### Layout

- Navigation latérale fixe à gauche (icônes + libellés), fond Blanc Papier, sans ombre lourde.
- Grille modulaire à cartes pour les vues de synthèse (tableau de bord, listes employés/candidats).
- Vues tabulaires denses pour l'historique (présence, demandes administratives) — colonnes alignées, hairlines Gris Dossier, pas de zébra criard.
- Aucune animation décorative. Micro-interactions discrètes uniquement : survol de carte (légère élévation), transition d'état de statut (changement de couleur du tag, pas de rebond).

---

## 3. Écrans à livrer

### 3.1 Authentification
- Écran de connexion simple (identifiant / mot de passe), sans inscription — deux rôles seulement (Admin, Manager). Pas de fioriture, juste le logo HB Développement, le formulaire, un message d'erreur clair en cas d'échec.

### 3.2 Tableau de bord — vue Admin
Compteurs cliquables (EF-DASH-01) :
- Employés actifs (total + répartition par département)
- Demandes administratives en attente
- Anomalies de pointage du jour
- Candidatures en attente de traitement
- Stagiaires dont la fin de contrat est dans les 7 jours

Chaque compteur = une carte au motif "badge", avec le chiffre en typographie Display et un tag de statut coloré. Cliquer redirige vers la liste filtrée.

### 3.3 Tableau de bord — vue Manager
Version réduite (EF-DASH-02) : effectif de l'équipe, demandes en attente pour l'équipe, anomalies du jour de l'équipe. Même langage visuel, moins de cartes, aucune action d'approbation visible (Manager = lecture seule).

### 3.4 Dossier Employé
- **Liste** avec filtres (département, manager, type de contrat, statut) — vue tableau dense.
- **Fiche détail** : identité, poste, département, type de contrat, manager rattaché, documents liés, historique de présence résumé, solde de congés affiché en évidence.
- **Carte badge** générée : afficher la version numérique (aperçu écran) et un aperçu du PDF imprimable 85×54mm, avec bouton "Régénérer la carte" et action "Bloquer le QR code".
- État "Manager à réaffecter" si l'on tente de désactiver un manager avec des employés actifs rattachés (EF-EMP-08) — message d'erreur explicite listant les employés concernés.

### 3.5 Présence
- Historique de pointage d'un employé ou d'une équipe, filtrable par période — tableau avec colonnes check-in / check-out / temps de présence calculé / statut (à l'heure, retard, départ anticipé, anomalie).
- Tags de statut colorés selon la palette (Sauge = à l'heure, Ambre = retard, Corail = anomalie).
- Écran de configuration de l'horaire de référence (EF-ATT-07) : heures de début/fin matin et après-midi, tolérance en minutes, avec historique des versions précédentes et leur date d'effet.

### 3.6 Recrutement
- **Vue pipeline** en colonnes : Reçu → Présélectionné → Entretien → Décision → Embauché / Rejeté, plus les statuts transversaux "En attente" et "Archivée" affichés à part.
- Chaque candidature = carte badge avec score de correspondance IA, mots-clés extraits, indicateur "analyse en attente" si l'IA n'a pas encore traité le CV.
- **Fiche candidat** : données extraites par l'IA, justification du score, historique du pipeline, zone de saisie du résultat d'entretien (Manager) — favorable / défavorable + commentaire.
- Bandeau "Suggestion de réactivation" pour les candidatures en attente qui matchent une nouvelle offre, avec mots-clés communs mis en évidence et bouton de validation manuelle par l'Admin.

### 3.7 Demandes Administratives
- Liste filtrable par type (congé, bon de sortie, document libre), statut, période.
- **Formulaire de demande de congé** : affichage en évidence du solde disponible de l'employé avant toute saisie de dates, avec blocage visuel immédiat si la durée dépasse le solde (EF-ADM-01) — message d'erreur en langage clair, pas technique.
- Vue "registre de mouvements" (ledger) pour le solde de congés : chronologie des mouvements (+1,5j/mois, −1j approbation, +1j annulation) plutôt qu'un simple chiffre — donne à l'Admin RH une traçabilité complète.
- Formulaire bon de sortie (créneau horaire + motif).
- Formulaire d'envoi de document libre (upload + destinataire + message).

### 3.8 Documents RH
- Vue "certificats de stage à traiter" : liste des stagiaires en fin de contrat, notification J-3, bouton de confirmation d'envoi avec aperçu du PDF pré-rempli.
- Journal d'envoi (date, envoyé par, destinataire) pour audit, avec option de renvoi manuel.

---

## 4. Composants clés à définir comme composants réutilisables

- Carte "badge" (variantes : stat, employé, candidat)
- Tag de statut (variantes : succès, attention, alerte, neutre — cohérent avec la palette)
- Ligne de tableau dense avec statut
- Colonne de pipeline (kanban)
- Barre de solde de congés (avec seuil visuel)
- Champ de formulaire avec message d'erreur en langage clair
- Item de chronologie (registre de mouvements)

---

## 5. Ton et rédaction (copy)

- Toujours à la voix active, du point de vue de l'utilisateur : "Approuver la demande", pas "Soumettre".
- Messages d'erreur non techniques : "Ce congé dépasse le solde disponible (X jours restants)", jamais de jargon système.
- Écrans vides = invitation à agir : "Aucune candidature en attente — les nouvelles candidatures apparaîtront ici automatiquement."
- Vocabulaire cohérent d'un écran à l'autre : un statut garde le même nom partout (ex. "En attente" désigne toujours la même chose, que ce soit pour une demande ou une candidature).

---

## 6. Contraintes non négociables

- Thème **clair uniquement**, aucun mode sombre.
- Interface entièrement en **français**.
- Desktop uniquement (pas de responsive mobile à prévoir).
- Accessibilité : contraste suffisant sur tous les tags de statut, focus clavier visible.
- Aucun élément décoratif qui ne serve pas une donnée réelle du cahier des charges — chaque couleur, chaque icône a une justification fonctionnelle.

---

## 7. Livrable attendu

Un fichier Figma avec :
1. Une page "Design System" (tokens couleur, typographie, composants listés en §4).
2. Une page par écran listé en §3, en haute fidélité.
3. Un flux annoté montrant le parcours "candidature → embauche → fiche employé → carte badge" comme démonstration du cycle de vie employé.