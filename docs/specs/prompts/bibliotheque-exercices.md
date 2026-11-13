# Prompt Claude Code — Bibliothèque d'exercices illustrée (Open Exercise Illustrations)

## Objectif

Intégrer dans l'app la base **Open Exercise Illustrations** (415 exercices, images de départ et d'arrivée, métadonnées) pour que chaque exercice ou machine ait une fiche illustrée, et que je puisse ajouter un exercice à mon catalogue en le choisissant dans cette bibliothèque.

L'app doit rester publiable sur le Play Store : tout ce qui est embarqué doit être sous licence compatible et crédité.

## Source

- Dépôt : `https://github.com/hitmanrepo/open-exercise-illustrations`
- Version à utiliser, à figer : commit `2786aefa4b36a08faeca8114e0d1eb0af3ef6732` (données v1.0.0)
- Licence : **CC0 1.0** (domaine public, usage commercial autorisé, attribution non requise)
- Contenu : `exercises.json` (métadonnées), `exercises.csv`, `images/{id}/start.webp`, `end.webp`, parfois `mid.webp` (mouvements en 3 temps) ou `hold.webp` (maintiens et étirements). 796 images WebP, environ 24 Mo.

Commence par cloner le dépôt à ce commit et **lis son README en entier** : il décrit le schéma de `exercises.json`, les listes de valeurs et les limites connues. Ne devine aucun champ.

Conserve dans le projet, hors APK (dossier `third_party/open-exercise-illustrations/`), une copie du `LICENSE`, du `README.md` et un fichier `SOURCE.md` indiquant l'URL, le commit et la date de téléchargement. C'est la preuve de licence en cas de publication.

## Intégration des données

- **Images** : embarquées dans l'APK (`assets/exercise_library/images/{id}/…`), l'app doit fonctionner hors ligne. Ne recompresse pas et ne recadre pas les images. Si le poids pose un problème, propose-moi une solution avant d'agir.
- **Métadonnées** : importées dans une table Room dédiée, en lecture seule, distincte de ma table d'exercices personnels. Prévois un numéro de version de la bibliothèque pour pouvoir la mettre à jour plus tard sans toucher à mes données.
- **Variantes** : le fichier contient 44 variantes (`variants`) qui réutilisent les images d'un autre exercice via `imageOf`, parfois avec un champ `difference`. Importe-les comme entrées de bibliothèque pointant vers les images de l'exercice d'origine, et affiche `difference` sur la fiche.
- **Lien avec mon catalogue** : ajoute à mon entité d'exercice un champ nullable `libraryId`. Migration Room explicite, aucune perte de données, test de migration.

## Correspondance avec mes 23 exercices existants

Renseigne `libraryId` par une migration de données, en cherchant mes exercices par leur nom exact :

| Mon exercice | `libraryId` |
|---|---|
| Rameur | `rowing-machine` |
| Chest press | `chest-press-machine` |
| Pec machine | `pec-deck` |
| Chest incline | `plate-loaded-incline-chest-press` |
| Shoulder press | `shoulder-press-machine` |
| Élévations latérales | `lateral-raises` |
| Reverse fly | `reverse-pec-deck` |
| Pull down | `lat-pulldown` |
| Pull down prise neutre | `close-grip-lat-pulldown` |
| Rowing machine | `machine-row` |
| Lower back | `back-extensions` |
| Curl biceps | `biceps-curl` |
| Arm extension | `machine-triceps-extension` |
| Hack squat | `hack-squat` |
| Leg press | `seated-leg-press` |
| Leg curl | `seated-leg-curl` |
| Leg extension | `leg-extension` |
| Abducteurs machine | `hip-abduction-machine` |
| Mollets debout | `standing-calf-raise-machine` |
| Abdominal crunch machine | `machine-abdominal-crunch` |
| Chaise romaine | `captains-chair-knee-raise` |
| Planche latérale | `side-plank` |
| Planche | `plank` |

Deux correspondances sont approximatives (Lower back : la bibliothèque montre un banc à lombaires, pas la machine assise ; Leg press et Leg curl : assis ou non selon ma machine). Je dois donc pouvoir **changer ou retirer l'image liée** depuis l'écran de modification d'un exercice, en choisissant une autre entrée de la bibliothèque.

Vérifie que les 23 identifiants existent dans `exercises.json` et signale-moi tout écart au lieu de le contourner.

## Fiche exercice

Une fiche affiche, dans cet ordre :

1. **Illustration animée** : fondu enchaîné en boucle entre les images, dans l'ordre du champ `frames` (maintien d'environ 0,9 s, fondu d'environ 0,35 s). Une image `hold` reste fixe. Toucher l'image met l'animation en pause.
2. Nom, groupe musculaire, matériel.
3. **Muscles** : principaux et secondaires.
4. Niveau, type de mouvement, mention « un côté à la fois » si `unilateral` est vrai (les images ne montrent qu'un côté).
5. **Mes repères** : mes champs existants (réglages, note de charge), s'il s'agit d'un exercice de mon catalogue.
6. **Consignes** (Réglages, Exécution, Erreurs fréquentes, Sécurité) : uniquement si une fiche texte existe déjà pour cet exercice (import du dossier `Forme/Machines`). **N'invente aucune consigne** pour les autres exercices : la bibliothèque ne fournit pas de texte d'exécution.

Affichage des images : tailles variables selon l'exercice (768×1024 le plus souvent, aussi 1024×768, 1536×1024, 886×886). Utilise `width` et `height` pour le ratio, ajustement « contain », dans une carte sombre à coins arrondis (les fonds d'image sont sombres).

Si j'ai pris ma propre photo de la machine, elle reste affichée en plus, sous l'illustration : elle sert à reconnaître la machine dans la salle.

## Bibliothèque et ajout au catalogue

- Nouvel écran **Bibliothèque** dans la Configuration : recherche par nom et alias, filtres par muscle principal, par matériel et par lieu (`suitableFor` : salle, machines, maison, poids du corps).
- Liste avec vignette (image de départ), nom et muscle principal.
- Bouton **Ajouter à mon catalogue** : crée un exercice personnel pré-rempli (nom, groupe musculaire, type déduit du matériel et de la catégorie, `libraryId`). Je complète ensuite l'incrément de charge et mes réglages.
- Un exercice déjà présent dans mon catalogue est signalé dans la liste.

## Libellés français

Les noms d'exercices restent **en anglais**, comme dans mon catalogue actuel (Chest press, Pull down). Seules les valeurs de liste sont traduites :

| Clé muscle | Libellé |
|---|---|
| chest | Pectoraux |
| shoulders | Épaules |
| triceps | Triceps |
| biceps | Biceps |
| forearms | Avant-bras |
| lats | Grand dorsal |
| back | Haut et milieu du dos |
| traps | Trapèzes |
| lower_back | Lombaires |
| core | Abdominaux |
| obliques | Obliques |
| glutes | Fessiers |
| quads | Quadriceps |
| hamstrings | Ischio-jambiers |
| calves | Mollets |
| tibialis | Tibial antérieur |
| neck | Cou |
| adductors | Adducteurs |

Niveau : `beginner` Débutant, `intermediate` Intermédiaire, `advanced` Avancé.
Catégorie : `compound` Polyarticulaire, `isolation` Isolation, `conditioning` Cardio / conditionnement, `mobility` Mobilité, `stretch` Étirement.
Lieu : `gym` Salle, `machines` Machines, `home` Maison, `bodyweight` Poids du corps.

Pour les 41 identifiants de matériel, les types de mouvement (`movementPattern`), les types de machine (`machineType`) et les vues, propose-moi une table de traduction dans `strings.xml` et fais-la moi valider avant de finaliser. Regroupe mon champ « groupe musculaire » existant à partir du premier muscle principal.

## Mentions obligatoires

- Écran **Sources et licences** (dans la Configuration) : Open Exercise Illustrations, CC0 1.0, lien vers le dépôt. L'attribution n'est pas exigée, je la veux quand même.
- Sous chaque illustration, en petit : « Illustration générée par IA, à titre indicatif. Vérifie le mouvement auprès d'une source qualifiée. »
- Le README du dépôt signale que des détails peuvent être inexacts (prise, trajet des câbles, géométrie des machines). Ne présente jamais ces images comme une référence technique.

## Qualité

- Tests : import de `exercises.json` (415 exercices, 44 variantes, aucune image manquante dans les assets), migration Room, correspondance des 23 exercices, recherche et filtres.
- Un test échoue si un `libraryId` de mon catalogue ne correspond à aucune entrée de la bibliothèque.
- L'animation ne doit pas ralentir le défilement de la liste : vignettes fixes dans les listes, animation uniquement sur la fiche.
- Indique-moi le poids de l'APK avant et après.

## Méthode attendue

1. Clone le dépôt au commit indiqué, lis le README, puis propose-moi : le schéma Room, la stratégie d'embarquement des images, la table de traduction et la liste des écrans modifiés. Attends ma validation.
2. Implémente ensuite par étapes vérifiables : import et tests → migration et correspondance → fiche animée → écran Bibliothèque → mentions et licences.
3. Signale toute ambiguïté au lieu de supposer.
