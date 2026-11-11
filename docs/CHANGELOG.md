# Historique des versions

Une ligne par version publiée (la release GitHub de chaque version reprend sa ligne). Les versions 1.0 à 2.6
s'appelaient « Forme », 2.7 à 2.13 « GymBro ».

## Versions

| Version | Contenu |
|---|---|
| **V1.0** ✅ | Socle technique + module Sport (séances, timer de repos, historique, records, poids, sauvegarde JSON) |
| **V1.1** ✅ | Profil, repas (code-barres Open Food Facts, table Ciqual 2025 embarquée, favoris), jauge protéines (1,8 g/kg/j, modifiable), glucides / lipides / kcal, whey 1 dose/jour, créatine + rappel |
| **V1.1.1** ✅ | Volume hebdo par groupe musculaire + régularité (onglet Semaine), remplacement d'une machine occupée, supersets (2 exercices), note « pour la prochaine fois » |
| **V1.2** ✅ | Sauvegarde automatique : fichier JSON choisi une fois (ex. dossier Google Drive), réécrit après chaque séance et chaque jour ; 3 copies locales |
| plus tard | Intégration Drive complète (API Drive v3, `drive.file`, copies datées, bilans Markdown) — en réflexion |
| **V2.1** ✅ | Fréquence cardiaque : FC en direct de la montre (Bluetooth, profil Heart Rate standard), complément Health Connect (Mi Fitness), zones, calories, bilan avec courbe et séries, onglet Semaine, dépense dans Nutrition |
| **V2.2** ✅ | Objectif calories (prise de masse / maintien / sèche, majoré des séances), conseil protéines et whey (jusqu'à 2 doses), bilan nutrition de la semaine, CI GitHub (APK signé) |
| **V2.3** ✅ | Coaching : 1RM estimé, détection de plateau (séance + historique), conseil de semaine de décharge sur l'accueil |
| **V2.4** ✅ | Analyse IA : fichier Markdown du jour (consignes + profil + séance + nutrition + semaine) partagé à l'app IA choisie (Claude, ChatGPT…) |
| **V2.5** ✅ | Journal nutrition en anneaux : kcal et protéines (consommé / objectif, reste), glucides et lipides face à leur cible conseillée |
| **V2.6** ✅ | Raccourcis d'icône (séance, repas, scanner), widget d'écran d'accueil 4 × 2, sauvegardes relues et vérifiées |
| **V2.7** ✅ | Nouveau nom **GymBro** et nouvelle icône (G bleu de l'app, fond blanc, icône à thème Android 13+) ; accueil sans titre, onglet « Accueil » avec le G ; nouveaux fichiers `gymbro-*` |
| **V2.8** ✅ | Bibliothèque d'exercices illustrée (Open Exercise Illustrations, CC0) — étape 1 : données embarquées, table en lecture seule, liens des 23 exercices |
| **V2.9** ✅ | Étape 2 : fiche exercice animée (séance, historique, catalogue), choix / retrait de l'illustration |
| **V2.10** ✅ | Étape 3 : écran Bibliothèque (recherche, filtres, ajout au catalogue) |
| **V2.11** ✅ | Étape 4 : écran Sources et licences, poids de l'APK mesuré ; release et APK nommés GymBro |
| **V2.12** ✅ | Volume hebdomadaire par muscle de la bibliothèque (séries fractionnaires), repère par muscle réglable, export IA |
| **V2.13** ✅ | Séance conseillée selon les muscles en retard dans « Nouvelle séance », ligne « Muscles en retard » dans l'export IA |
| **V2.14** ✅ | Nouveau nom **GymFlemme** : app, release et APK `gymflemme-*`, fichiers de sauvegarde et d'analyse IA |
| **V2.15** ✅ | Petites touches d'humour sur les écrans existants, « Mode sérieux », accord selon le sexe du profil |
| **V2.16** ✅ | Retour après une pause (accueil, charges à 80 % sur demande), ligne quand l'objectif de la semaine passée n'est pas tenu |
| **V2.17** ✅ | Badges de régularité (onglet Historique → Badges, annonce au récap et à l'accueil), base v7, sauvegarde v7 |
| **V2.18** ✅ | Badges à paliers, ponctuels et secrets, points et niveau global, écran Badges depuis l'accueil, annonce unique, récap « Tes badges déjà gagnés », base v8 (badges v1 convertis), sauvegarde v8 |
| **V2.19** ✅ | Défis mensuels (tirage déterministe, éligibilité, carte sur l'accueil, onglet Défis, 20 points, annonce du 1er du mois désactivable), base v9, sauvegarde v9 |
| **V2.19.1** ✅ | Correctif : le bouton « Enregistrer » de « Nouvel aliment » restait grisé |
| **V2.19.2** ✅ | Modifier ou supprimer un aliment aussi depuis Nutrition → Ajouter (fiche de l'aliment), comme dans Config → Mes aliments |
| **V2.19.3** ✅ | La FC de la notification n'est plus mise à jour écran éteint (chaque mise à jour rallumait l'écran sur certains téléphones) ; rafraîchie dès que l'écran se rallume |
| **V2.19.4** ✅ | Badges et défis calculés hors du fil de l'interface, une seule fois pour l'accueil et l'écran Badges, détection des records en un passage (3 ans d'historique : ~280 → ~95 ms) |
| **V2.20** ✅ | Compléments alimentaires : créatine, oméga-3, vitamine D, magnésium, zinc, multivitamines, vitamine C et compléments perso ; chacun activable, quantité et unité par jour, rappel à l'heure choisie (Config → Compléments) ; une case par complément actif dans Nutrition et sur l'accueil ; export IA ; base v10, sauvegarde v10 |
| **V2.20.1** ✅ | Correctif : sur un téléphone lent, la proposition de charges allégées (retour après une pause) pouvait s'afficher hors de l'écran, au-dessus du premier exercice ; la séance remonte pour la montrer |
| **V2.21** ✅ | Plats personnalisés : un plat est un aliment composé d'ingrédients (Ciqual, scannés, perso), avec poids cuit facultatif et nombre de portions ; créé depuis Config → Mes aliments, Nutrition → Ajouter ou « Enregistrer comme plat » sur un repas du journal ; base v11, sauvegarde v11 |
| **V2.22** ✅ | Accueil « bento » : grille de tuiles (prochaine séance, calories, protéines, défi du mois, compléments, niveau), police Space Grotesk, thèmes clair et sombre, barre de navigation flottante (Accueil, Séances, Nutrition, Badges ; Config dans l'en-tête), prénom dans le profil ; base v12, sauvegarde v12 |
| **V2.23** ✅ | Barre de navigation à 5 onglets (Réglages, ex-Config, rejoint Accueil, Séances, Nutrition et Badges), icônes Material Symbols ; « Sources et licences » devient « À propos » ; mode développeur (7 appuis sur la version) qui seul affiche le journal Bluetooth de Montre cardio ; « Réglages et sauvegarde » devient « Préférences et sauvegarde » |

Hors périmètre V1.1 : Drive, Health Connect, BLE, partage vers Claude, widgets, Wear OS, multi-utilisateur, analyse photo.

## Idées pour plus tard

- Photos des machines : import manuel exercice par exercice (déjà possible), pas d'import en masse.
- Intégration Drive complète (API Drive v3, copies datées, bilans Markdown) — en réflexion.

---

