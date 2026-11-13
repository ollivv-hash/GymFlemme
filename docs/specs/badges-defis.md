# GymFlemme — Badges, défis et niveaux (complément)

Complément au fichier « GymFlemme — Catalogue des textes à touche d'humour », déjà transmis. **Ce fichier remplace sa section 15 « Badges »** ; toutes les autres sections et consignes de ce catalogue restent valables (ton, mode sérieux, `strings.xml`, tirage sans répétition, textes toujours neutres).

## Principes

- **Toujours un objectif visible.** Chaque badge à paliers affiche le palier suivant et une barre de progression.
- **Effort et régularité uniquement.** Aucun badge lié au poids corporel, à la perte de kilos ou à l'alimentation autre que l'objectif protéines.
- **Pas d'incitation au surentraînement.** Aucun badge pour s'entraîner tous les jours ; les séries comptent en semaines, pas en jours.
- **Rien ne se perd.** Un badge ou un palier obtenu reste acquis, même après une longue pause.
- **Ton** : celui du catalogue (taquin, complice, jamais sur le corps). Chaque message a sa version neutre pour le mode sérieux.

## 1. Reprise des 10 badges existants

| Badge du catalogue initial | Devient |
|---|---|
| Pas mort | Badge ponctuel (section 3), inchangé |
| Bien parti | Badge ponctuel (section 3), inchangé |
| Récidiviste | Badge à paliers **Récidiviste** (section 2), palier Bronze |
| Pilier de la salle | Fusionné dans **Récidiviste**, palier Argent (50 séances) |
| Plus fort que le canapé | Badge à paliers, palier Bronze |
| Lève-tôt malgré lui | Badge ponctuel, inchangé |
| Régulier | Badge ponctuel, inchangé |
| Record en vue | Badge ponctuel (1er record), inchangé ; la suite passe par **Collectionneur de records** |
| Touche-à-tout | Badge à paliers **Touche-à-tout** (10 / 20 / 35 / 50 exercices) |
| Créatine fidèle | Badge à paliers ; l'ancien seuil de 30 jours devient le palier Argent |

Si les badges de la première version sont déjà implémentés : migration de données explicite, sans perte, avec test. Un badge déjà obtenu est conservé et converti dans le palier correspondant.

## 2. Badges à paliers

Points par palier : **Bronze 10 · Argent 25 · Or 50 · Platine 100**.

| Badge | Mesure | Bronze | Argent | Or | Platine |
|---|---|---|---|---|---|
| Récidiviste | Séances terminées | 10 | 50 | 150 | 300 |
| Plus fort que le canapé | Semaines consécutives avec au moins 1 séance | 4 | 12 | 26 | 52 |
| Déménageur | Tonnage total soulevé | 10 t | 50 t | 200 t | 500 t |
| Galérien | Distance cumulée au rameur | 10 km | 50 km | 200 km | 500 km |
| Collectionneur de records | Records battus | 5 | 25 | 75 | 150 |
| Créatine fidèle | Jours consécutifs avec créatine cochée | 7 | 30 | 90 | 365 |
| Shaker d'or | Jours avec objectif protéines atteint (non consécutifs) | 7 | 30 | 100 | 250 |
| Touche-à-tout | Exercices différents réalisés au moins une fois | 10 | 20 | 35 | 50 |

### Messages d'obtention

| Badge | Bronze | Argent | Or | Platine |
|---|---|---|---|---|
| Récidiviste | Dix séances. Tu reviens toujours sur les lieux du crime. | Cinquante séances. Tu fais partie des meubles. | 150 séances. La salle devrait te verser un loyer. | 300 séances. On grave ton nom sur un banc ? |
| Plus fort que le canapé | Un mois sans laisser gagner le canapé. Respect. | Trois mois. Le canapé a déposé plainte. | Six mois. Le canapé s'est trouvé quelqu'un d'autre. | Un an. Le canapé ne te reconnaît plus. |
| Déménageur | 10 tonnes soulevées. Un camion, mais en morceaux. | 50 tonnes. Tu pourrais faire un déménagement. | 200 tonnes. Un avion de ligne, à peu près. | 500 tonnes. On arrête de compter, ça devient gênant. |
| Galérien | 10 km de rameur. Sans quitter la salle. | 50 km. La Manche, c'est pour bientôt. | 200 km. Tu as presque traversé la Manche, aller-retour. | 500 km. Capitaine, on rentre au port ? |
| Collectionneur de records | Cinq records. Tu prends goût au progrès. | 25 records. La flemme ne sait plus où se cacher. | 75 records. Tu progresses plus vite que tes excuses. | 150 records. Toi contre toi, et tu gagnes. |
| Créatine fidèle | Une semaine sans oubli. Bien joué. | Trente jours sans oubli. Constance exemplaire. | Trois mois. Ta cuillère te connaît par cœur. | Un an sans oubli. On n'a jamais vu ça. |
| Shaker d'or | 7 jours d'objectif protéines. Ton shaker reprend du service. | 30 jours. Ton shaker est fier de toi. | 100 jours. Ton shaker mérite une médaille aussi. | 250 jours. Le shaker d'or est à toi. |
| Touche-à-tout | Dix exercices différents. Tu explores ! | Vingt exercices. Plus aucune machine ne te fait peur. | 35 exercices. Tu connais la salle mieux que le coach. | 50 exercices. Il reste des machines que tu n'as pas essayées ? |

Version neutre de tous ces messages : « Badge {nom} : palier {palier} obtenu ».

Les comparaisons (camion, avion, Manche) sont des images, pas des valeurs exactes ; ne pas les afficher comme des données chiffrées.

## 3. Badges ponctuels et secrets

Points : **10** par badge ponctuel, **15** par badge secret.

Les badges **secrets** apparaissent masqués dans la liste (« ??? », sans condition affichée) jusqu'à leur obtention.

| Badge | Type | Condition | Message | 
|---|---|---|---|
| Pas mort | Ponctuel | 1re séance terminée | Première séance. Tu es vivant, et tu l'as fait ! |
| Bien parti | Ponctuel | 5 séances terminées | Cinq séances. Ça ressemble dangereusement à une habitude. |
| Régulier | Ponctuel | 3 séances dans la même semaine | Trois séances cette semaine. On ne te reconnaît plus. |
| Record en vue | Ponctuel | 1er record battu | Ton tout premier record. Et sûrement pas le dernier. |
| Lève-tôt malgré lui | Ponctuel | Séance commencée avant 8 h | Une séance avant 8 h. Qui t'a forcé ? |
| Oiseau de nuit | Ponctuel | Séance commencée après 21 h | La salle ferme bientôt, mais pas toi. |
| Triple A | Ponctuel | Séances A, B et C terminées dans la même semaine | La rotation complète. Chef-d'œuvre. |
| Pas de jaloux | Ponctuel | Tous les groupes musculaires (hors Cardio) travaillés dans la même semaine | Aucun muscle oublié. Ils sont tous contents. |
| Lundi, même pas peur | Secret | 4 séances terminées un lundi (au total) | Le lundi ne te fait plus peur. |
| Dimanche sportif | Secret | Séance terminée un dimanche | Même le dimanche. Respect. |
| Le marathon | Secret | Séance de plus de 75 minutes | Tu as dormi à la salle ? |
| Le retour du roi | Secret | Séance terminée après au moins 30 jours sans séance | Il est revenu ! On t'avait gardé ta place. |
| Anniversaire | Secret | 365 jours après la première séance, avec au moins une séance dans les 30 derniers jours | Un an déjà. Tu te souviens de tes débuts ? |
| Nouvel An | Secret | Séance terminée un 1er janvier | Tu tiens déjà ta bonne résolution. |

Version neutre : « Badge obtenu : {nom} ».

## 4. Défis mensuels

- **Un défi par mois**, affiché sur l'accueil du 1er au dernier jour du mois, avec sa progression.
- Réussi : badge daté « Défi de {mois} {année} » et **20 points**. Raté : rien ne se perd, aucun message négatif.
- Choix du défi : tirage **déterministe** à partir de l'année et du mois (même défi si l'app est réinstallée ou restaurée), sans reprendre le défi du mois précédent.
- Un défi n'est éligible que si l'utilisateur a les données nécessaires : défis Rameur seulement s'il a un exercice Rameur dans son catalogue, défi protéines seulement s'il a saisi des repas le mois précédent, défi Triple séance seulement s'il a au moins 3 modèles dans sa rotation.

| Défi | Objectif | Annonce (début de mois) | Réussite |
|---|---|---|---|
| Douze coups | 12 séances dans le mois | Ce mois-ci : 12 séances. Une tous les deux-trois jours, c'est jouable. | Douze séances ! Le mois est plié. |
| Rameur du mois | 30 km de rameur dans le mois | Ce mois-ci : 30 km de rameur. Hissez haut ! | 30 km ramés. Tu peux ranger les rames. |
| Chasseur de records | 3 records battus dans le mois | Ce mois-ci : 3 records à faire tomber. | Trois records ! La chasse a été bonne. |
| Zéro semaine blanche | Au moins 1 séance chaque semaine du mois | Ce mois-ci : pas une semaine sans séance. | Aucune semaine blanche. Le canapé est vexé. |
| Curieux | 3 exercices jamais réalisés | Ce mois-ci : 3 exercices que tu n'as jamais faits. | Trois nouveautés testées. L'aventure, la vraie. |
| Protéines au rendez-vous | Objectif protéines atteint 20 jours | Ce mois-ci : 20 jours avec ton objectif protéines. | 20 jours au top. Ton shaker a fait des heures sup'. |
| Triple séance | Chaque modèle de la rotation fait au moins 3 fois | Ce mois-ci : chaque séance de ta rotation, 3 fois minimum. | Rotation complète, trois fois. Impeccable. |

Version neutre : annonce « Défi du mois : {objectif} », réussite « Défi du mois réussi ».

Une semaine qui chevauche deux mois compte pour le défi « Zéro semaine blanche » du mois qui contient son lundi.

## 5. Niveau global

Total de points = somme des points de tous les badges, paliers et défis obtenus. Le niveau s'affiche sur l'accueil (titre + barre vers le niveau suivant).

| Niveau | Titre | Points requis |
|---|---|---|
| 1 | Flemmard débutant | 0 |
| 2 | Flemmard repenti | 30 |
| 3 | Habitué | 100 |
| 4 | Sportif du dimanche | 250 |
| 5 | Pilier de salle | 500 |
| 6 | Machine de guerre | 900 |
| 7 | Légende de la fonte | 1 400 |

Passage de niveau :

| Humour | Neutre |
|---|---|
| Niveau {n} : {titre} ! La flemme perd du terrain. | Niveau {n} atteint : {titre} |
| Te voilà {titre}. On ne t'arrête plus. | |
| {titre} ! Ton canapé n'en revient pas. | |

En mode sérieux, les titres de niveau sont remplacés par « Niveau {n} ».

## 6. Règles de calcul

- **Séance terminée** : séance avec une heure de fin et au moins une série ou une entrée cardio validée. La séance d'amorce (seed) ne compte jamais.
- **Semaine** : semaine ISO, du lundi au dimanche, heure locale du téléphone. La série « Plus fort que le canapé » se met à jour à la fin de chaque semaine ; la semaine en cours ne la casse jamais.
- **Tonnage** : somme de `loadKg × reps` sur les séries validées des exercices `WEIGHTED`, **hors séries d'échauffement**. Les exercices au poids du corps, chronométrés et cardio n'entrent pas dans le tonnage.
- **Distance rameur** : somme de `distanceM` des entrées cardio dont l'exercice est lié à `libraryId = rowing-machine`, ou nommé « Rameur » si le lien n'existe pas.
- **Record** : la même définition que celle déjà utilisée en fin de séance (meilleure charge ou meilleur volume par exercice). Un record égalé ne compte pas.
- **Exercice différent** : identifiant d'exercice distinct avec au moins une série validée ; les exercices archivés comptent.
- **Créatine** : jour avec la case cochée ; un jour non coché remet la série à zéro, sans retirer les paliers obtenus.
- **Objectif protéines atteint** : total du jour ≥ cible du jour, calculée avec le poids en vigueur ce jour-là.
- **Heures** (avant 8 h, après 21 h) : heure de début de séance, heure locale.

## 7. Affichage

- **Écran Badges** (accessible depuis l'accueil, via le niveau) : trois onglets, « Paliers », « Ponctuels » et « Défis ». Badge obtenu en couleur avec sa date ; badge à obtenir en grisé avec sa condition et sa progression (« 37 / 50 séances ») ; badge secret non obtenu affiché « ??? ».
- **Accueil** : niveau et titre, barre de progression vers le niveau suivant, défi du mois avec sa progression.
- **Obtention** : affichée en fin de séance dans le récapitulatif (pas d'interruption pendant les séries). Pour la créatine et les protéines, au moment de la saisie qui débloque le badge.
- Plusieurs badges obtenus en même temps : un seul écran récapitulatif, pas une succession de pop-ups.
- Pas de notification push pour les badges ; seule l'annonce du défi du mois peut faire l'objet d'une notification, désactivable.

## Consignes d'intégration pour Claude Code

- Tous les textes de ce fichier vont dans `strings.xml`, avec leur version neutre, selon les règles du premier catalogue.
- Seuils, points, niveaux et défis dans une **configuration centrale** (objet Kotlin ou JSON embarqué), pas dispersés dans le code : je dois pouvoir ajuster un seuil sans chercher.
- **Calcul rétroactif** au premier lancement de cette version : les badges, paliers et points sont calculés à partir de tout l'historique existant. Les badges débloqués ainsi apparaissent dans un récapitulatif unique « Tes badges déjà gagnés ».
- Le calcul doit être **idempotent** : relancer le calcul ne doit jamais créer de doublon ni de nouvelle notification d'obtention.
- Enregistre les badges obtenus avec leur date et leur palier ; inclus-les dans la sauvegarde JSON et la synchro Google Drive.
- Tests unitaires : chaque règle de la section 6, chaque palier, la série de semaines (y compris la semaine en cours et les changements d'année), le tirage déterministe du défi mensuel et son éligibilité, la migration des badges de la première version, l'idempotence.
- Propose-moi le modèle de données et l'écran Badges avant d'implémenter.
- Ne modifie pas le ton des textes ; signale toute phrase trop longue pour l'écran au lieu de la réécrire.
