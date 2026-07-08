1. Écran Authentification
Créer un écran de connexion simple et centré, fond Blanc Papier :

Logo HB Développement en haut (repris de la navigation existante).
Formulaire à deux champs : "Identifiant" et "Mot de passe" (masqué), un seul bouton "Se connecter" en Encre Marine.
Pas de lien d'inscription ni de récupération de compte visible par défaut (les comptes sont créés par l'Admin, cf. écran 9).
Emplacement pour message d'erreur clair sous le formulaire, en Corail Alerte, texte non technique : ex. "Identifiant ou mot de passe incorrect."
Aucune fioriture, aucune illustration décorative — l'écran doit rester sobre et institutionnel.


2. Écran Liste des Employés
Nouvel écran (actuellement seule la fiche détail existe) :

En-tête : titre "Employés", sous-titre avec compteur total ("47 employés"), bouton principal en haut à droite "+ Ajouter un employé" (même style que "+ Ajouter une candidature").
Barre de filtres sous l'en-tête : département (menu déroulant), manager (menu déroulant), type de contrat (CDI / CDD / Stagiaire / Stagiaire rémunéré), statut (Actif / Inactif) — filtres combinables.
Tableau dense, hairlines Gris Dossier, colonnes : Nom, Poste, Département, Manager, Type de contrat, Statut (tag coloré : Sauge = actif, Gris = inactif), Date d'embauche.
Chaque ligne cliquable, redirige vers la fiche détail (écran déjà existant).
Clic sur "+ Ajouter un employé" ouvre un panneau latéral (drawer) avec le formulaire de création : nom, prénom, contact, poste, département, date d'embauche, type de contrat, manager rattaché. Message de confirmation à la validation : "Employé créé — carte badge générée."
Prévoir que ce même panneau puisse s'ouvrir pré-rempli (champs identité remplis, département/contrat/date/manager vides) lorsqu'il est déclenché depuis une fiche candidat au statut "Embauché".


3. État "Manager à réaffecter" (EF-EMP-08)
Sur la fiche employé d'un Manager (variante de l'écran fiche détail existant), lorsque l'Admin tente de désactiver ce Manager :

Si des employés actifs lui sont rattachés : afficher une modale de blocage, fond Blanc Papier, bordure Corail Alerte en haut.

Titre : "Impossible de désactiver ce manager"
Texte : "X employés sont encore rattachés à [Nom du manager]. Réaffectez-les avant de continuer."
Liste des employés concernés (nom, poste, département), chacun avec un menu déroulant "Réaffecter à →" proposant les autres managers actifs.
Bouton "Confirmer les réaffectations" désactivé tant que tous les employés n'ont pas un nouveau manager sélectionné.


Si aucun employé actif n'est rattaché : la désactivation se fait directement, avec une simple modale de confirmation standard (pas de liste).


4. Écran Configuration Horaire de Référence (EF-ATT-07)
Nouvel écran, accessible depuis Présence (bouton "Configurer l'horaire" ou onglet dédié) :

Formulaire avec 5 champs : heure de début matin, heure de fin matin, heure de début après-midi, heure de fin après-midi, tolérance en minutes.
Valeurs par défaut affichées : 08h30–13h00 / 14h00–17h00, tolérance 10 minutes.
Bouton "Enregistrer" — préciser en note sous le formulaire : "Ce changement s'applique uniquement aux pointages futurs. Les pointages passés restent évalués selon l'horaire en vigueur à leur date."
Sous le formulaire, tableau "Historique des versions" : colonnes Horaire, Tolérance, Date d'effet, Modifié par — hairlines Gris Dossier, lecture seule.


5. Tableau de bord — vue Manager (EF-DASH-02)
Variante réduite du dashboard Admin déjà existant :

Mêmes cartes stat "badge" mais seulement 3 : Effectif de l'équipe, Demandes en attente (équipe), Anomalies du jour (équipe).
Pas de carte "Candidatures en attente" globale ni "Fins de contrat" globale (hors périmètre Manager).
Section "Demandes récentes" limitée aux employés du département du Manager, sans boutons Approuver/Rejeter (lecture seule stricte, EF-AUTH-03).
Pas de bandeau "Suggestion de réactivation" (réservé à l'Admin).
Peut inclure un bloc "Entretiens à réaliser" listant les candidatures de son département au stade "Entretien", avec lien vers la fiche candidat.


6. Formulaire "Demande de congé" (EF-ADM-01)
Contenu de l'onglet "Demande de congé" déjà présent dans la nav de l'écran Demandes (actuellement vide/non ouvert) :

Sélecteur d'employé en haut (menu déroulant recherchable).
Dès qu'un employé est sélectionné : afficher immédiatement, en évidence (carte avec typographie Display pour le chiffre), le solde disponible actuel — ex. "12 jours disponibles".
Champs : granularité (journée complète / demi-journée matin / demi-journée après-midi), dates de début et fin (ou date + créneau pour demi-journée), motif (facultatif).
Calcul en temps réel de la durée demandée, affiché à côté du solde pour comparaison directe.
Blocage visuel immédiat si la durée dépasse le solde : le bouton "Enregistrer la demande" devient inactif (grisé) et un message apparaît en Corail Alerte sous les champs de dates : "Ce congé dépasse le solde disponible (12 jours restants)." — texte non technique, jamais de jargon.
Bouton "Enregistrer la demande" actif uniquement si la durée est valide.


7. Onglet "Registre des mouvements" (EF-ADM-03)
Contenu de l'onglet déjà présent dans la nav de l'écran Demandes :

Sélecteur d'employé en haut.
Solde actuel affiché en grand (typographie Display), sans dénominateur fixe (pas de "X/25j" ni "X/18j" — le plafond n'est atteint que si aucun congé n'est pris dans l'année, donc pas un maximum à afficher comme une barre de progression classique).
En dessous, chronologie verticale (timeline), item par item, du plus récent au plus ancien :

Chaque ligne = date, description du mouvement, valeur (+1,5j accumulation mensuelle / −1j ou −0,5j approbation / +1j ou +0,5j annulation / mouvement d'ajustement manuel), solde cumulé après ce mouvement.
Utiliser Sauge pour les mouvements positifs, Ambre ou neutre pour les négatifs (pas de rouge, ce ne sont pas des anomalies).
Chaque ligne utilise IBM Plex Mono pour les valeurs numériques (cohérent avec le traitement "donnée système").


C'est cet écran qui doit être atteignable depuis le lien "Voir le registre des mouvements →" sur la fiche employé (cf. corrections précédentes).


8. Formulaire "Bon de sortie" (EF-ADM-04)
Nouveau sous-écran ou modale, accessible depuis le bouton "+ Nouvelle demande" avec type "Bon de sortie" sélectionné :

Sélecteur d'employé.
Champs : date, heure de départ, heure de retour prévue, motif (champ texte court).
Aucune interaction avec le solde de congés — ne pas afficher de barre de solde sur cet écran.
Bouton "Enregistrer" simple, pas de blocage particulier hormis validation de champs obligatoires.


9. Formulaire "Document libre" (EF-ADM-09)
Nouveau sous-écran ou modale, accessible depuis "+ Nouvelle demande" avec type "Document" sélectionné, ou directement depuis Documents RH :

Sélecteur d'employé destinataire.
Zone d'upload de fichier (glisser-déposer + bouton parcourir).
Champ "Message" (texte libre, pré-rempli avec un modèle générique éditable).
Bouton unique "Envoyer le document" — pas d'étape d'approbation intermédiaire (contrairement au congé). À la validation, le document est directement envoyé et journalisé dans le Journal d'envoi (écran Documents RH existant).


10. Fiche Candidat détaillée (EF-REC section pipeline)
Nouvel écran, ouvert au clic sur une carte du pipeline Recrutement :

En-tête : nom du candidat, poste visé, score de correspondance IA en grand (typographie Display, couleur selon seuil : Sauge si élevé, Ambre si moyen, Corail si faible), statut actuel du pipeline en tag.
Section "Données extraites" : coordonnées, compétences, années d'expérience — toutes issues de l'analyse IA (EF-REC-04), avec mention "Extrait automatiquement" en IBM Plex Mono si c'est une donnée système.
Section "Justification du score" : texte court généré par l'IA expliquant le score.
Mots-clés extraits sous forme de tags (repris du style déjà vu sur les cartes pipeline).
Section "Historique du pipeline" : chronologie des changements de statut avec dates (même style visuel que le registre de mouvements, item 7).
Si le candidat est au stade "Entretien" : zone de saisie réservée au Manager — choix "Favorable" / "Défavorable" (boutons ou toggle) + champ commentaire. Si l'utilisateur connecté est Manager, cette zone est éditable ; si Admin, elle est visible en lecture avec le résultat déjà saisi.
Si le candidat est "En attente" avec suggestion de réactivation active : bandeau reprenant le style du dashboard, avec mots-clés communs surlignés et bouton "Réactiver" (validation manuelle Admin).
Bouton "Passer à l'étape suivante →" en bas, visible selon le rôle et le statut courant.


11. Écran Création d'Offre d'emploi (EF-REC-01)
Nouvel écran, accessible depuis Recrutement (bouton "+ Nouvelle offre" à ajouter à côté de "+ Ajouter une candidature") :

Formulaire : intitulé du poste, description (zone de texte enrichi simple), département cible (menu déroulant), statut (Ouverte / Fermée, toggle).
Bouton "Publier l'offre".
Note sous le formulaire : "À la création, les candidatures en attente compatibles seront automatiquement suggérées pour réactivation." (référence visuelle au mécanisme EF-REC-12).
Prévoir aussi une liste des offres existantes (tableau simple : intitulé, département, statut, nombre de candidatures actives, date de création), accessible en onglet ou sous-page de l'écran Recrutement.


12. Écran Import Excel/CSV (EF-EMP-07)
Nouvel écran, accessible depuis Employés (bouton ou lien secondaire "Importer des données") :

Zone d'upload de fichier (glisser-déposer), avec texte d'aide : "Formats acceptés : .xlsx, .csv — employés, historique de présence, soldes de congés, ou tout autre type de donnée à migrer."
Après upload, aperçu tabulaire des premières lignes détectées, avec mapping des colonnes du fichier vers les champs du système (menus déroulants par colonne).
Bouton "Lancer l'import".
Zone "Rapport d'erreurs" après traitement : tableau des lignes invalides avec le motif de rejet par ligne (ex. "Ligne 14 : date d'embauche invalide"), en Corail Alerte pour les lignes en erreur.
Résumé en haut : "X lignes importées avec succès, Y lignes en erreur."


13. Écran Gestion des Comptes Utilisateurs (EF-AUTH-07)
Nouvel écran, accessible uniquement à l'Admin, depuis un lien "Paramètres" ou "Comptes" à ajouter en bas de la navigation latérale (distinct du profil "Amal Medah" déjà présent) :

Tableau des comptes existants : Nom, Rôle (Admin / Manager), Statut (Actif/Inactif), Dernière connexion.
Bouton "+ Créer un compte" en haut à droite, ouvrant un formulaire : nom, identifiant, rôle (Admin / Manager), département (si Manager), mot de passe temporaire.
Actions par ligne : "Modifier", "Désactiver".
Important : ce tableau ne doit pas utiliser la nomenclature EMP-XXX (réservée aux fiches employé) — utiliser un identifiant de compte distinct ou aucun identifiant visible, uniquement le nom et le rôle.