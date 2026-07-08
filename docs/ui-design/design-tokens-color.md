Ajouter un nouveau token de couleur au Design System (page "Design System", section tokens couleur) :

Nom : Rose Marque
Usage : identité de marque uniquement — logo, écran de connexion, indicateur d'état actif de la navigation. Jamais utilisé pour un tag de statut, une donnée de tableau, ou un indicateur métier (ces usages restent réservés à Sauge/Ambre/Corail).
Valeur : prélever la teinte exacte du logo HB fourni, puis produire une variante légèrement désaturée et assombrie pour tout usage sur fond clair (vérifier le contraste WCAG AA minimum si le rose touche du texte).

Appliquer ce token uniquement à :

Le logo dans le bandeau supérieur de la navigation latérale (déjà présent, aucune modification de la forme du logo lui-même).
L'écran de connexion : un accent discret autour ou sous le logo (ex. fine ligne, léger halo), sans remplir de grande surface.
L'indicateur visuel de l'élément de navigation actif (remplacer ou compléter l'actuel fond Encre Marine plein par une pastille ou une bordure gauche en Rose Marque, à tester visuellement pour vérifier que ça reste sobre et lisible).

Ne pas appliquer ce token aux boutons d'action principaux, ni à aucun tag de statut, ni à aucune donnée chiffrée du tableau de bord — ces éléments conservent leur traitement déjà validé (Encre Marine pour les actions, Sauge/Ambre/Corail pour les statuts).


Rappel du système de design à respecter strictement : palette (Blanc Papier #F7F7F4, Encre Marine #1B2A41, Sauge Administrative #4A7C6B, Ambre Vigilance #C87F3A, Corail Alerte #C1495A, Gris Dossier #D8D4CC), typographie (Source Serif 4 titres/chiffres, IBM Plex Sans/Inter corps, IBM Plex Mono identifiants), navigation latérale existante, motif de coin "badge" sur les cartes, thème clair uniquement, français, desktop.

Accès à l'écran
Ajouter un onglet ou un lien secondaire "Départements" sur l'écran Liste des Employés déjà existant (à côté du titre "Employés", sous forme de deux onglets : Employés | Départements — même pattern que les onglets déjà utilisés sur l'écran Demandes Administratives : "Liste des demandes" / "Demande de congé" / "Registre des mouvements").

1. Vue principale — Liste des départements

En-tête : titre "Départements", sous-titre avec compteur ("4 départements"), bouton principal en haut à droite "+ Créer un département" (même style que "+ Ajouter un employé").
Tableau dense, hairlines Gris Dossier, colonnes :

Nom du département (ex. Technologie, Marketing, Ventes, RH)
Manager rattaché (nom, avec renvoi cliquable vers sa fiche employé)
Employés actifs (chiffre, cliquable, redirige vers la liste employés filtrée sur ce département — cohérent avec le principe déjà appliqué aux compteurs du dashboard, EF-DASH-03)
Statut (tag : Sauge "Actif" / Gris "Inactif")


Actions par ligne (icônes ou liens à droite) : "Modifier", "Désactiver".
Chaque ligne peut afficher un mini-badge de répartition sous le nom du manager si utile (ex. "Depuis le 15/03/2021"), mais rester sobre — pas de surcharge visuelle.


2. Modale — Créer un département
Déclenchée par "+ Créer un département" :

Titre : "Nouveau département"
Champ "Nom du département" (texte libre).
Champ "Manager rattaché" (menu déroulant recherchable, liste des comptes Manager existants — cf. écran Gestion des comptes utilisateurs).
Note sous le champ manager : "Chaque département doit avoir un manager actif rattaché." — si aucun manager disponible n'existe encore, afficher un message d'aide : "Aucun manager disponible. Créez d'abord un compte Manager depuis Gestion des comptes."
Bouton "Créer le département" — désactivé tant que les deux champs ne sont pas remplis.


3. Modale — Modifier un département
Déclenchée par "Modifier" sur une ligne :

Mêmes champs que la création (nom, manager rattaché), pré-remplis.
Si le manager est changé, aucun impact sur les employés déjà rattachés au département (ils restent liés au département, pas individuellement au manager sortant) — pas de confirmation supplémentaire nécessaire ici, contrairement à la désactivation.
Bouton "Enregistrer les modifications".


4. État de blocage — Désactivation avec employés actifs (règle analogue à EF-EMP-08)
Déclenché par "Désactiver" sur un département ayant des employés actifs rattachés :

Modale de blocage, fond Blanc Papier, bordure Corail Alerte en haut (même pattern que l'état "Manager à réaffecter" déjà spécifié).
Titre : "Impossible de désactiver ce département"
Texte : "X employés sont encore rattachés au département [Nom]. Réaffectez-les à un autre département avant de continuer."
Liste des employés concernés (nom, poste, manager actuel), chacun avec un menu déroulant "Réaffecter à →" proposant les autres départements actifs.
Bouton "Confirmer les réaffectations" désactivé tant que tous les employés n'ont pas un nouveau département sélectionné.
Note : ce réaffectation groupée doit aussi permettre de réassigner le manager de chaque employé si le nouveau département a un manager différent (menu déroulant secondaire qui apparaît une fois le département choisi, pré-rempli avec le manager du département cible mais modifiable).

5. État simple — Désactivation sans employé actif
Si le département n'a aucun employé actif rattaché :

Modale de confirmation standard (pas de liste) : "Désactiver le département [Nom] ? Cette action peut être annulée en le réactivant depuis cet écran." Bouton "Confirmer" / "Annuler".
Une fois désactivé, le département apparaît dans la liste avec le tag "Inactif" (Gris Dossier), et une action "Réactiver" remplace "Désactiver"/"Modifier" sur cette ligne.


6. Cohérence avec les écrans existants

Le champ "Département" affiché lors de la création/modification d'un employé (écran Fiche Employé, panneau "+ Ajouter un employé") doit désormais alimenter son menu déroulant depuis cette liste de départements — ne plus être un champ texte libre. Ajouter cette précision en annotation Figma sur l'écran Fiche Employé existant.
Les filtres "département" déjà présents sur les écrans Liste des Employés, Présence, et Demandes Administratives doivent également refléter dynamiquement les départements actifs définis ici.
Sur l'écran Configuration (EF-CFG-01), aucun lien direct requis vers cet écran — la gestion des départements reste dans le module Employés, pas dans Configuration, car c'est une donnée métier structurante et non un paramètre système.