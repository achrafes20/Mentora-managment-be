Prompt Figma — Complément Addendum (Blocs A, B, C, D)
Rappel du système de design à respecter strictement : palette (Blanc Papier #F7F7F4, Encre Marine #1B2A41, Sauge Administrative #4A7C6B, Ambre Vigilance #C87F3A, Corail Alerte #C1495A, Gris Dossier #D8D4CC), typographie (Source Serif 4 titres/chiffres, IBM Plex Sans/Inter corps, IBM Plex Mono identifiants), navigation latérale existante, motif de coin "badge", thème clair uniquement, français, desktop.

A. Suivi de fin de contrat CDD
A1 — Fiche employé : champ conditionnel
Sur l'écran Fiche Employé (onglet Profil), dans la section "Informations personnelles" :

Si le type de contrat sélectionné est CDD, afficher un champ supplémentaire "Date de fin de contrat prévue" (optionnel, sélecteur de date).
Ce champ n'apparaît jamais pour CDI, Stagiaire, ou Stagiaire rémunéré.
Si une date est renseignée et que l'échéance approche (≤15 jours ouvrables), afficher un badge discret à côté du nom de l'employé en haut de la fiche, style Ambre Vigilance : "Fin de contrat prévue le [date]".
Prévoir un état visuel distinct pour la date de départ effective (si elle diffère de la date prévue, saisie séparément à la désactivation) — ex. un second champ en lecture seule qui n'apparaît qu'après désactivation : "Date de départ effective : [date]", pour bien distinguer les deux notions.

A2 — Documents RH : élargir la liste "à traiter"
Sur l'écran Documents RH, renommer la section "Certificats de stage à traiter" en "Documents de fin de contrat à traiter", et faire apparaître dans la même liste :

Les stagiaires en fin de contrat (comportement déjà existant, J-3, notification unique).
Les CDD en fin de contrat prévue (nouveau), avec badge de délai distinct : "J-15" au lieu de "J-3"/"J-5", pour bien signaler le délai différent (EF-DOC-13).
Si une notification J-15 pour un CDD n'a pas été traitée et atteint J-3, afficher un second badge cumulatif "Relance J-3" en Corail Alerte à côté du premier (seule exception au principe "notification unique" — spécifique aux CDD, EF-DOC-14).
Le type de document généré diffère selon le cas : bouton "Confirmer l'envoi" génère soit "Certificat de stage" (stagiaire), soit "Certificat de travail" (CDD) — le libellé du bouton et de l'aperçu PDF doit refléter le bon type de document.


B. Délégation temporaire d'approbation
B1 — Écran de gestion de la délégation
Nouvel écran, accessible depuis un lien "Délégation" dans la navigation latérale ou depuis l'écran Gestion des comptes utilisateurs (onglet supplémentaire) :

Titre : "Délégation d'approbation"
Si aucune délégation active : état vide avec bouton "+ Déléguer temporairement".
Formulaire de création : sélecteur de délégué (liste des comptes Admin existants + comptes Manager), date de début, date de fin, note explicative sous le formulaire rappelant le périmètre : "Le délégué obtient les droits d'approbation des demandes administratives et de décision de recrutement. Il n'obtient pas les droits de gestion des comptes ni de configuration système."
Une fois active : carte récapitulative en haut de l'écran (style carte "badge") affichant le délégué, la période, et un bouton "Révoquer" (action immédiate, avec confirmation).
Section "Historique des délégations" en dessous : tableau (Délégué, Période, Statut : Active/Expirée/Révoquée, Révoquée par si applicable).

B2 — Bandeau sur le tableau de bord Admin (EF-DASH-05)
Sur l'écran Tableau de bord Admin, ajouter — uniquement si une délégation est active — un bandeau en haut de page, style neutre/informatif (fond Gris Dossier clair, texte Encre Marine, pas de couleur d'alerte car ce n'est pas un problème) :

Texte : "Délégation active — [Nom du délégué] peut approuver les demandes jusqu'au [date de fin]." avec lien "Gérer →" vers l'écran B1.

B3 — Marquage des actions en délégation dans l'historique
Sur les écrans où une approbation/rejet est visible (Liste des demandes, Fiche candidat), lorsque l'action a été effectuée par un délégué, ajouter une mention discrète sous le statut : "Approuvé par [Nom délégué] (en délégation d'Amal Medah)" — texte petit, gris, IBM Plex Sans.

C. Centre de notifications in-app
C1 — Icône et badge dans la navigation
Ajouter une icône cloche dans le bandeau supérieur ou en haut de la navigation latérale, visible sur tous les écrans (Admin et Manager) :

Badge numérique rouge/Corail Alerte en surimpression si des notifications sont non lues, affichant le compte (ex. "3"), sans dépasser "9+" si plus de 9.
Clic ouvre un panneau déroulant (dropdown), pas une nouvelle page, pour un accès rapide.

C2 — Panneau déroulant de notifications

Largeur modérée (~360px), fond Blanc Papier, ombre légère.
Liste des notifications récentes, chacune avec : icône de type (approbation, nouveau candidat, anomalie, rappel fin de contrat), texte court, horodatage relatif ("il y a 2h"), point de couleur si non lue (Sauge ou Ambre selon le type).
Chaque notification cliquable → redirige vers l'élément concerné (fiche employé, demande, candidature).
En bas du panneau : lien "Tout marquer comme lu" et lien "Voir toutes les notifications →" menant à l'écran C3.

C3 — Écran complet des notifications
Nouvel écran (accessible via "Voir toutes les notifications") :

Liste complète, plus dense qu'un dropdown, avec filtres simples en haut : Toutes / Non lues, et par type (menu déroulant).
Même structure de ligne que le panneau déroulant mais avec plus d'espace et la date complète.
Bouton "Tout marquer comme lu" en haut à droite.
Note en bas de page, discrète : "Les notifications sont conservées 90 jours."


D. Journal d'audit
D1 — Écran de consultation
Nouvel écran, accessible depuis la navigation (lien "Journal d'audit", visible Admin uniquement) :

En-tête : titre "Journal d'audit", sous-titre "Lecture seule — aucune modification possible."
Barre de filtres : utilisateur (menu déroulant), type d'action (menu déroulant : Création / Modification / Approbation / Rejet / Désactivation / Connexion...), module concerné (Employé / Présence / Recrutement / Demande administrative / Configuration), période (sélecteur de dates).
Champ de recherche texte libre au-dessus du tableau, placeholder : "Rechercher un utilisateur ou un élément (ex. nom d'un employé)".
Tableau dense, hairlines Gris Dossier, colonnes : Date/Heure (IBM Plex Mono), Utilisateur, Action, Module, Élément concerné, Détail (texte court).
Si une action a été faite en délégation (cf. B3), l'afficher clairement dans la colonne Utilisateur : "[Délégué] (en délégation)".
Bouton "Exporter" en haut à droite (icône export), ouvrant un choix de format Excel / PDF.
Aucune action de modification ou suppression visible sur aucune ligne — écran strictement en lecture.