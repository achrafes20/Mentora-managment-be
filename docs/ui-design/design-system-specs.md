Rappel du système de design à respecter strictement : palette (Blanc Papier #F7F7F4, Encre Marine #1B2A41, Sauge Administrative #4A7C6B, Ambre Vigilance #C87F3A, Corail Alerte #C1495A, Gris Dossier #D8D4CC), typographie (Source Serif 4 titres/chiffres, IBM Plex Sans/Inter corps, IBM Plex Mono identifiants), navigation latérale existante, motif de coin "badge", thème clair uniquement, français, desktop.

1. Authentification — compléments (EF-AUTH-08/09/10)
1.1 — Lien "Mot de passe oublié"
Sur l'écran de connexion existant, ajouter un lien discret sous le formulaire : "Mot de passe oublié ?" menant à un second écran :

Champ "Identifiant ou e-mail", bouton "Envoyer le lien de réinitialisation".
Message de confirmation neutre après envoi (ne pas révéler si le compte existe) : "Si un compte correspond à ces informations, un e-mail a été envoyé."

1.2 — Nouvel écran "Réinitialiser le mot de passe"
Accessible via le lien reçu par e-mail : deux champs (nouveau mot de passe, confirmation), avec rappel visuel de la politique de complexité sous les champs (10 caractères min., majuscules/minuscules/chiffres) et validation en temps réel (coche verte par critère rempli). Bouton "Réinitialiser".
1.3 — État "Compte verrouillé"
Sur l'écran de connexion, après un nombre configurable de tentatives échouées : remplacer le message d'erreur standard par un message Corail Alerte : "Compte temporairement verrouillé suite à plusieurs tentatives échouées. Réessayez dans [X] minutes, ou contactez un administrateur."
1.4 — Expiration de session
Modale légère apparaissant sur n'importe quel écran après inactivité prolongée, fond semi-transparent : "Votre session a expiré pour des raisons de sécurité." avec bouton "Se reconnecter" ramenant à l'écran de connexion.

2. Gestion des Départements (EF-EMP-10)
Nouvel écran, accessible depuis Employés (onglet ou lien "Départements") :

Tableau : Nom du département, Manager rattaché, Nombre d'employés actifs, Statut.
Bouton "+ Créer un département" (modale : nom, manager assigné).
Action "Modifier" par ligne (nom, manager).
Action "Désactiver" par ligne : si des employés actifs sont rattachés, afficher le même type de blocage que pour un Manager (liste des employés à réaffecter au préalable, cf. écran déjà spécifié pour EF-EMP-08) ; sinon désactivation directe avec confirmation simple.


3. Transfert d'employé (EF-EMP-11)
Sur la fiche employé existante, ajouter une action "Transférer" (bouton secondaire, à côté de "Régénérer la carte"/"Bloquer QR") :

Ouvre une modale : nouveau département (menu déroulant), nouveau manager (menu déroulant filtré selon le département sélectionné), date d'effet.
Note sous le formulaire : "Ce transfert n'affecte pas le solde de congés ni l'historique de présence déjà enregistré."
Après validation, ajouter une entrée dans une section "Historique des rattachements" sur la fiche employé (nouvelle sous-section sous les infos personnelles) : Date, Ancien département/manager → Nouveau département/manager.


4. Recherche texte libre (EF-EMP-12, EF-REC-06)
Sur l'écran Liste des Employés (déjà spécifié) et sur l'écran Recrutement (pipeline) :

Ajouter une barre de recherche en haut de chaque écran, à côté des filtres existants, placeholder : "Rechercher un employé (nom, prénom, e-mail)" / "Rechercher une candidature (nom, e-mail)".
Résultats filtrés en temps réel, combinable avec les filtres déjà en place (département, statut, etc.).


5. Génération de cartes en lot (EF-EMP-14)
Sur l'écran Liste des Employés :

Ajouter des cases à cocher sur chaque ligne du tableau (mode sélection multiple), activable via une icône ou un bouton "Sélectionner".
Une fois une sélection faite, une barre d'action flottante apparaît en bas de l'écran : "X employés sélectionnés" avec bouton "Générer les cartes (PDF groupé)".
Au clic, déclenche un état de génération (spinner discret) puis proposition de téléchargement du PDF regroupé, format 85×54mm par carte, prêt à découper.


6. E-mail de rejet candidat (EF-REC-14)
Sur la Fiche Candidat détaillée (déjà spécifiée), lorsque l'Admin fait passer le statut à "Rejeté" :

Ouvrir automatiquement une modale de confirmation avant validation finale : "Un e-mail de rejet sera envoyé à [nom candidat]."
Corps de message pré-rempli, éditable (zone de texte), ton neutre et professionnel par défaut.
Bouton "Confirmer et envoyer" / "Annuler".
Une fois envoyé, ajouter cette action à la section "Historique du pipeline" de la fiche candidat, avec mention "E-mail de rejet envoyé le [date]".


7. Calendrier des jours fériés (EF-ADM-10)
Nouvel écran, accessible depuis Demandes ou depuis Configuration (cf. item 10) :

Vue calendrier annuel simplifiée ou tableau liste (plus simple à maquetter en desktop dense) : Date, Nom du jour férié, Type (Fixe / Mobile).
Bouton "+ Ajouter un jour férié" (modale : date, nom).
Jours fixes pré-remplis à l'installation, modifiables ; jours mobiles (Aïd al-Fitr, Aïd al-Adha, Achoura, Mouloud) ajoutés manuellement par l'Admin chaque année, avec une icône distinctive (ex. croissant de lune) à côté du nom pour les distinguer visuellement des jours fixes.
Note en bas : "Un jour férié inclus dans une période de congé approuvée n'est pas décompté du solde."


8. Workflow de départ CDI/CDD (EF-DOC-08→11)
8.1 — Déclencheur sur la fiche employé
Lorsque l'Admin clique sur "Désactiver" pour un employé CDI/CDD (fiche employé existante), ouvrir une modale en plusieurs étapes plutôt qu'une simple confirmation :
Étape 1 — Informations de départ

Champ "Date de départ" (sélecteur de date).
Champ "Motif" (menu déroulant : Démission, Licenciement, Fin de CDD, Rupture, Autre).

Étape 2 — Récapitulatif

Affichage informatif (non éditable) : "Solde de congés restant non pris : X jours" — préciser sous ce chiffre : "Information à titre indicatif — aucune indemnisation n'est calculée par le système."
Rappel que le QR code sera automatiquement bloqué à la date de départ saisie.

Étape 3 — Confirmation

Bouton "Confirmer le départ" — déclenche la désactivation de la fiche.

8.2 — Génération du certificat de travail
Une fois le départ confirmé, sur la fiche employé (devenue inactive), ajouter une section similaire à celle des certificats de stage vue sur Documents RH :

Carte "Certificat de travail" avec statut (À générer / Envoyé), bouton "Aperçu PDF" et "Confirmer l'envoi" (même pattern que le certificat de stage).
Une fois envoyé, apparaît dans le Journal d'envoi global (écran Documents RH), avec le bon libellé "Certificat de travail — [Nom]" pour le distinguer visuellement des certificats de stage.
Bouton "Renvoyer" disponible ensuite, avec traçabilité (même pattern que EF-DOC-07).


9. Module Export & Reporting (EF-EXP-01→04)
Sur chacun des 3 écrans concernés, ajouter un bouton secondaire "Exporter" en haut à droite, à côté des boutons d'action déjà existants :

Liste des Employés : bouton "Exporter" → menu avec choix "Excel (.xlsx)" / "PDF", applique les filtres actifs à l'export.
Présence (historique d'un employé ou d'une équipe) : bouton "Exporter" → même choix de format, génère une feuille de présence mensuelle.
Demandes Administratives (liste) : bouton "Exporter" → même choix, applique les filtres actifs (type, statut, période).

Comportement identique partout : clic → petit menu déroulant avec les 2 formats → déclenchement du téléchargement, sans état de chargement long à prévoir (génération à la demande, immédiate).

10. Écran Configuration & Paramétrage (EF-CFG-01/02)
Nouvel écran central, accessible depuis un lien "Configuration" dans la navigation latérale (section basse, proche du profil Admin), regroupant en onglets ou sections empilées :

Section "Horaire de référence" : reprend le contenu déjà spécifié pour l'écran EF-ATT-07 (heures, tolérance, historique des versions) — soit en lien vers l'écran existant, soit intégré ici comme sous-section.
Section "Jours fériés" : lien ou intégration vers l'écran du point 7 ci-dessus.
Section "Recrutement" : deux champs — seuil de score pour suggestion de réactivation (%, slider ou champ numérique), fenêtre de rétention en mois (champ numérique, ex. 6).
Section "Sécurité des comptes" : nombre de tentatives avant verrouillage (champ numérique, défaut 5), délai de déverrouillage automatique (en minutes), politique de mot de passe (rappel en lecture seule des règles fixes : 10 caractères min., majuscules/minuscules/chiffres).
Chaque section a son propre bouton "Enregistrer" (pas un bouton global unique, pour éviter les erreurs de sauvegarde groupée).
Note globale en bas de page : "Toute modification est journalisée dans le journal d'audit."


11. Compléments de l'addendum encore non intégrés (rappel)
Si ce n'est pas déjà fait depuis le prompt précédent, les écrans suivants restent à produire pour couvrir entièrement l'addendum :

Écran Délégation d'approbation + bandeau dashboard (EF-AUTH-11→15, EF-DASH-05)
Centre de notifications in-app (icône, panneau déroulant, écran complet — EF-NOTIF-01→06)
Écran Journal d'audit consultable (EF-CFG-03→06) — à noter : cet écran peut maintenant être positionné comme un onglet supplémentaire de l'écran Configuration (point 10 ci-dessus), plutôt qu'un écran séparé, puisque les deux sont désormais dans le même module transverse Admin.