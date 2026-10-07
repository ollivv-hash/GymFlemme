# GymBro — App Sport & Nutrition (Android, usage personnel)

Application Android native de suivi des séances en salle (module **Sport**) et de la nutrition (module **Nutrition**).
Usage strictement personnel : un utilisateur, un téléphone, APK signé installé via `adb install`, sans Play Store.

> **Statut : V1.0 à V1.2 validées sur le téléphone. V2.1 (fréquence cardiaque : montre en direct + Health Connect) implémentée** :
> build, Lint et tests JVM au vert, reste à valider sur le téléphone.

- Nom : **GymBro** (2.7 ; s'appelait « Forme » avant). Package inchangé : `fr.olivier.forme` — le changer ferait une
  autre app pour Android (installation à côté, base vide), donc les noms internes (`Forme…` dans le code) restent.
- Interface en français, code (identifiants, commentaires) en anglais
- **Hors ligne d'abord** : tout fonctionne sans réseau ; seule la recherche d'un produit **jamais scanné** interroge Open Food Facts.
  Aucune API IA, aucun service payant, aucune clé d'API dans le code

---

## Feuille de route

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
| **V2.10** 🚧 | Étape 3 : écran Bibliothèque (recherche, filtres, ajout au catalogue) ; sources et licences à venir |

Hors périmètre V1.1 : Drive, Health Connect, BLE, partage vers Claude, widgets, Wear OS, multi-utilisateur, analyse photo.

### Idées pour plus tard

- Photos des machines : import manuel exercice par exercice (déjà possible), pas d'import en masse.
- Intégration Drive complète (API Drive v3, copies datées, bilans Markdown) — en réflexion.

---

## Stack technique

- Kotlin, Jetpack Compose, Material 3 (thème clair / sombre / système, **sombre par défaut**)
- MVVM : `ui` / `domain` / `data`, repositories, ViewModels exposant des `StateFlow`
- Room (SQLite) avec `exportSchema = true`, schémas versionnés dans `app/schemas/`, migrations explicites
- Hilt, Navigation Compose (routes typées), Coroutines / Flow, DataStore (Preferences)
- `minSdk = 28` (prérequis Health Connect), `targetSdk` = dernière version stable supportée par l'AGP utilisé
- Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)

### Dépendances et justification

| Dépendance | Pourquoi |
|---|---|
| AGP 9.4, Kotlin 2.4 (Kotlin intégré à AGP), plugin Compose Compiler, Gradle 9.8 | Socle (`compileSdk`/`targetSdk` 37) |
| KSP | Génération de code Room et Hilt (remplace kapt, déprécié) |
| Compose BOM, Material 3, `activity-compose`, `material-icons-core` | UI (`icons-extended` n'est plus maintenu : seules les icônes de base sont utilisées) |
| `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | `collectAsStateWithLifecycle`, ViewModels |
| `navigation-compose` | Navigation (routes typées via kotlinx.serialization) |
| Hilt + `hilt-lifecycle-viewmodel-compose` | Injection de dépendances, `hiltViewModel()` |
| Room (runtime, ktx, compiler, `room-testing`) | Persistance ; `room-testing` pour valider les migrations |
| `datastore-preferences` | Réglages |
| `kotlinx-serialization-json` | Export / import JSON, routes typées ; maintenu par JetBrains, sans réflexion |
| `kotlinx-coroutines` | Asynchronisme |
| `sh.calvin.reorderable` | Glisser-déposer dans une `LazyColumn` (absent de Compose) ; petite lib Apache 2, maintenue. **Seule dépendance tierce.** |
| `androidx.work:work-runtime` | Sauvegarde automatique quotidienne, rattrapage Health Connect après séance |
| `androidx.health.connect:connect-client` | Lecture de la FC et des calories écrites par Mi Fitness (V2.1) |
| `androidx.glance:glance-appwidget` | Widget d'écran d'accueil écrit en Compose (V2.6) ; bibliothèque officielle Google, remplace les `RemoteViews` écrites à la main |
| Tests : JUnit 4, `kotlinx-coroutines-test`, Turbine, `androidx.test`, Robolectric, `compose-ui-test-junit4`, `glance-appwidget-testing` | Tests unitaires et instrumentés ; Robolectric exécute aussi les tests Room sur le JVM (sans téléphone) |

Volontairement **sans** bibliothèque de graphiques (courbes dessinées en Compose Canvas) ni chargeur d'images (`BitmapFactory` sous-échantillonné suffit pour les photos de machines).

---

## Arborescence

Un seul module Gradle `:app`, organisé **par fonctionnalité** (le multi-module n'apporte rien ici ; la séparation `ui`/`domain`/`data` est faite par packages).

```
app_sport/
├── gradle/libs.versions.toml
├── settings.gradle.kts · build.gradle.kts · gradle.properties
├── keystore.properties.example        # le vrai keystore.properties est exclu du dépôt
├── README.md
└── app/
    ├── build.gradle.kts
    ├── schemas/…/FormeDatabase/1.json  # schéma Room versionné
    └── src/
        ├── main/java/fr/olivier/forme/
        │   ├── FormeApp.kt · MainActivity.kt
        │   ├── core/
        │   │   ├── db/          # FormeDatabase, Converters, Migrations, seed
        │   │   ├── settings/    # SettingsRepository (DataStore)
        │   │   ├── backup/      # DTO JSON versionné, BackupRepository (SAF)
        │   │   ├── time/        # Clock injectable (tests)
        │   │   └── di/          # modules Hilt
        │   ├── ui/              # theme, navigation, composants partagés (stepper, puces RIR, graphiques Canvas)
        │   └── feature/
        │       ├── sport/       # data · domain · timer · ui (accueil, séance, historique, catalogue, modèles)
        │       ├── bodyweight/  # data · domain · ui
        │       └── settings/    # réglages + sauvegarde
        ├── test/                # tests unitaires JVM + tests de fumée Compose (Robolectric)
        ├── sharedTest/          # tests Room (DAO, migration, aller-retour JSON) : exécutés par test ET androidTest
        └── androidTest/
```

V1.1 a ajouté `feature/nutrition/` (data · domain · reminder · ui) et la migration Room 1 → 2 ; V1.2 a ajouté la sauvegarde automatique dans `core/backup/` (`AutoBackupManager`, worker WorkManager) ; V2.1 a ajouté `feature/heart/` (data · domain · service · ui) et la migration 3 → 4 ; V2.6 a ajouté `core/launch/` (raccourcis) et `feature/widget/`.
V2.8 a ajouté `feature/library/` et `third_party/open-exercise-illustrations/` : `LICENSE`, `README.md` et `SOURCE.md`
(URL, commit figé `2786aef`, date) **hors APK** comme preuve de licence ; `assets/exercise_library/` (`exercises.json`
+ 796 images WebP, copies à l'octet près) ajouté aux assets par `app/build.gradle.kts`. APK release : 14,1 Mo → 37,5 Mo.
Le script `tools/ciqual/convert.py` régénère `app/src/main/assets/ciqual.tsv` depuis le fichier Excel publié par l'ANSES.

---

## Modèle de données (Room, version 6)

Unités : charges en **kg** (`Double`, pas de 0,5), durées en secondes, distances en mètres. `Instant` ↔ epoch millis, `LocalDate` ↔ epochDay. Les enums sont stockées en texte.

| Table | Colonnes principales | Remarques |
|---|---|---|
| `exercise` | id, name, muscleGroup, type, perSide, loadIncrementKg (2,5), **defaultTargetSets, defaultRepsMin, defaultRepsMax, defaultDurationSec**, settingsNote, loadNote, photoUri, archived, nextNote (v3), libraryId (v6) | Cible par défaut utilisée à l'ajout de l'exercice dans un modèle ou une séance libre. Archivé = masqué des choix, historique conservé. |
| `workout_template` | id, name, code, orderInRotation (nullable = hors rotation), archived | |
| `template_exercise` | id, templateId, exerciseId, position, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, optional, restSec (nullable → réglage par défaut), supersetGroup (v3) | Le modèle fait foi sur la cible du catalogue. |
| `session` | id, templateId (nullable, SET NULL), templateNameSnapshot, startedAt, endedAt (null = en cours), feeling 1–5, notes, isSeed | La séance en cours est persistée en base → reprise après kill du process. |
| `session_exercise` | id, sessionId, exerciseId, position, status (PENDING/DONE/SKIPPED), copie des cibles (sets, reps min/max, durée, repos, optionnel), supersetGroup, replacedExerciseId (v3) | Copie figée au démarrage : modifier ou archiver un modèle n'altère **jamais** une séance passée. Porte aussi réordonnancement / saut / ajout en cours de séance. |
| `set_entry` | id, sessionId, exerciseId, setIndex, loadKg, reps, durationSec, rir (0–5), isWarmup, completedAt | |
| `cardio_entry` | id, sessionId, exerciseId, durationSec, distanceM, pacePer500mSec, caloriesDisplayed | Allure calculée depuis durée/distance, modifiable. |
| `body_weight_entry` | id, date (unique), weightKg | Une pesée par jour (une nouvelle saisie remplace). |

Les exercices ne sont jamais supprimés (clés étrangères `RESTRICT`), seulement archivés.

Tables Nutrition (V1.1, migration explicite 1 → 2) :

| Table | Colonnes principales | Remarques |
|---|---|---|
| `profile` | id = 1, proteinTargetGPerKg (1,8), whey (prot./kcal/glucides/lipides pour 100 g, dose 30 g), creatineDoseG (5), rappel (actif, heure), FC (année de naissance, sexe, FC max), calories (taille, objectif, objectif repos personnalisé) | Ligne absente = valeurs par défaut. |
| `ciqual_food` + `ciqual_fts` | code, nom, groupe, kcal, protéines, glucides, lipides pour 100 g ; index plein texte | Référentiel ANSES en lecture seule, chargé depuis l'APK ; recherche insensible aux accents. **Non exporté.** |
| `food` | id, source (CIQUAL / OFF / CUSTOM), sourceCode (code Ciqual ou code-barres), nom, marque, valeurs /100 g, portion, favori, dernière utilisation | « Mes aliments » : perso, cache Open Food Facts, aliments Ciqual déjà utilisés (récents / favoris). |
| `meal_entry` | id, date, mealType, foodId?, nom, quantité (g), protéines, kcal, glucides, lipides, isWhey, heure | **Copie des valeurs** : modifier ou supprimer un aliment ne change jamais l'historique. |
| `creatine_entry` | id, date (unique), dose, heure | Une prise par jour. |
| `library_exercise` | id (ex. `leg-press`), nom, alias, variante (`imageOf`, `difference`), matériel, type de machine, muscles principaux / secondaires / `alsoTargets`, mouvement, catégorie, niveau, impact, unilatéral, échauffement, lieux, vue, images (`frames`, dossier, largeur, hauteur) | Bibliothèque **Open Exercise Illustrations** (CC0) en lecture seule : 415 exercices + 44 variantes, rechargée depuis l'APK quand sa version change (`ExerciseLibraryLoader.DATASET_VERSION`). Une variante montre les images de l'exercice d'origine mais garde son matériel, ses muscles et sa différence. **Non exportée.** |

Migration explicite **2 → 3** (V1.1.1) : 4 colonnes nullables ajoutées (`exercise.nextNote`, `template_exercise.supersetGroup`,
`session_exercise.supersetGroup`, `session_exercise.replacedExerciseId`), aucune donnée modifiée.

Migration explicite **3 → 4** (V2.1) : `profile.birthYear`, `profile.sex`, `profile.hrMaxOverride` (nullables) et la table
`session_heart` (une ligne par séance, supprimée avec elle) : courbe de FC ré-échantillonnée toutes les 5 s et stockée en
texte compact (`"72,75,0,80"`, 0 = pas de mesure, ~3 Ko/h), résumé **figé** au moment du calcul (moyenne, min, max, FC max
utilisée, secondes par zone), calories et leur source (montre ou estimation), source de la courbe (direct / Health Connect / mixte).

Migration explicite **5 → 6** (V2.8) : table `library_exercise` (remplie au démarrage, pas par la migration) et colonne
`exercise.libraryId` (nullable), renseignée pour les 23 exercices connus **par nom exact** (`ExerciseLibraryLinks`) ;
un exercice au nom différent reste sans illustration, à relier à la main. Les nouvelles installations reçoivent les liens
dès les données initiales.

Migration explicite **4 → 5** (V2.2) : `profile.heightCm`, `profile.nutritionGoal`, `profile.kcalTargetOverride` (nullables).

---

## Règles métier

- **Pré-remplissage** : chaque série reprend les valeurs (charge, reps, durée) de la dernière séance contenant l'exercice, quel que soit le modèle.
- **Progression** (suggestion « ↑ suggéré », jamais appliquée sans validation) — règle **par type d'exercice** :
  - `WEIGHTED` : si la dernière série de la dernière séance contenant l'exercice a un RIR ≥ 3 → charge + `loadIncrementKg`. Sans RIR saisi : si toutes les séries ont atteint `targetRepsMax` → même suggestion. (S'applique aussi aux machines d'abdos chargées.)
  - `BODYWEIGHT` : progression sur les reps (+1 rep par série) selon les mêmes conditions.
  - `TIMED` : +5 s par série selon les mêmes conditions.
  - Les séries d'échauffement et la séance d'amorce sont ignorées.
- **Records** par exercice : meilleure charge à reps ≥ `targetRepsMin` de la séance (1 rep minimum en séance libre) et meilleur volume (Σ charge × reps sur une séance). Échauffements exclus. Signalés en fin de séance.
- **Volume** : en kg pour `WEIGHTED` ; reps totales pour `BODYWEIGHT` ; temps total pour `TIMED` (jamais mélangés aux kg).
- **Rotation** : seuls les modèles non archivés avec `orderInRotation` participent. La séance proposée est celle qui suit la dernière séance terminée dont le modèle est **encore** dans la rotation (bouclage en fin de liste). « Bas du corps » ne décale pas la rotation. Le calcul est réactif : toute modification de la rotation met à jour la proposition.
- **Timer de repos** : démarré à la validation d'une série ; notification avec compte à rebours visible écran verrouillé + alarme exacte (son/vibration) en fin de repos, robuste au kill du process. Repos par défaut : 90 s.
- **Bilan texte** de fin de séance (date, modèle, exercices, séries, volume, comparaison avec la séance précédente du même modèle, records) copiable dans le presse-papiers — format réutilisé en V2 (partage Claude, export Markdown).
- **Unités** : kg uniquement.

### Améliorations séance (V1.1.1)

- **Onglet Historique → Semaine** : séries de travail (hors échauffement, hors cardio, hors séance d'amorce) par **groupe musculaire principal**
  de la semaine (lundi → dimanche), comparées à une zone repère **10 à 20 séries** (modifiable dans Configuration → Objectifs d'entraînement).
  Séances de la semaine / objectif (**3 par défaut**, modifiable), série de semaines consécutives où l'objectif est tenu,
  calendrier des 12 dernières semaines. L'accueil affiche « Cette semaine : x/3 séances ».
- **Remplacer une machine occupée** (menu ⋮ d'un exercice en séance) : pour **cette séance uniquement**, le modèle n'est pas modifié.
  Choix proposés : même groupe musculaire d'abord, puis tous les exercices. Cibles du créneau conservées si le type d'exercice est identique,
  sinon cibles par défaut du remplaçant. Si des séries sont déjà faites, l'original reste « fait » et le remplaçant est inséré juste après.
  Bilan : « Pull down (remplace Pull down prise neutre) ».
- **Supersets** de **2 exercices** adjacents, liés dans l'éditeur de modèle ou en séance (« Lier au suivant »). Pas de repos entre A et B ;
  le repos démarre après la série de B (ou dès que le partenaire est terminé). Déplacer ou retirer un exercice délie automatiquement la paire.
  Bilan : « (superset avec X) ».
- **Note pour la prochaine fois** (icône crayon d'un exercice) : choix rapides (« Monter la charge », « Trop dur, rester », « Douleur / gêne »,
  « Changer le réglage ») ou texte libre. Affichée en bandeau orange à la séance suivante jusqu'à « Vu ✓ », et dans l'historique de l'exercice.

### Analyse IA (V2.4)

- Boutons **« Analyse IA du jour »** (accueil) et **« Analyse IA de cette journée »** (bilan de séance) : GymBro écrit
  `gymbro-analyse-AAAA-MM-JJ.md` puis le partage (Android `ACTION_SEND`) à l'app IA choisie ; l'utilisateur n'a plus qu'à envoyer.
  GymBro n'appelle aucune IA et n'envoie rien elle-même.
- Contenu du fichier : **consignes** (prompt, modifiable), **profil** (âge, sexe, taille, poids et tendance 4 semaines, objectif,
  cibles protéines / calories, FC max), **séance(s) du jour** (séries avec RIR, dernière fois, 1RM estimé, stagnation, volume,
  records, FC et zones, calories, notes), **nutrition du jour** (totaux / objectifs, repas détaillés, whey, créatine),
  **semaine** (séances / objectif, séries par muscle, bilan nutrition, FC).
- **Historique pour graphiques** (2.4.1), en tableaux Markdown : pour chaque exercice de la séance, les 8 dernières séances
  (meilleure série, 1RM estimé) ; le poids sur 30 jours avec sa moyenne 7 jours ; les 7 derniers jours saisis de nutrition
  (protéines, calories, objectif calories du jour) ; les séries par groupe musculaire des 4 dernières semaines. Les consignes par
  défaut demandent un **artifact** (tableau de bord HTML) construit avec ces tableaux. Une consigne personnalisée reste
  inchangée : « Rétablir par défaut » récupère la nouvelle section « Visuel ».
- **Config → Analyse IA** : app IA par défaut (apps IA connues en tête, ou « Demander à chaque fois »), méthode d'envoi,
  consignes d'analyse modifiables (« Rétablir par défaut »), test avec la journée en cours.
- Méthode d'envoi testée avec Claude (lot 0) : **fichier seul** (par défaut ; la phrase d'accompagnement est ignorée quand un
  fichier est joint, les consignes sont donc dans le fichier) ou **texte** (pour les apps qui refusent les fichiers).
  Le contenu est aussi copié dans le presse-papiers. Le fichier est partagé depuis le cache via un `FileProvider` (lecture seule accordée à l'app cible).
- Le travail se fait dans la portée de l'application : quitter l'écran pendant la préparation (≈ 1 s) ne l'annule pas.

### Coaching (V2.3)

- **1RM estimé** (Epley : charge × (1 + reps / 30), charge seule pour 1 rep), uniquement sur les séries de **12 reps ou moins**
  et les exercices `WEIGHTED`. Affiché dans les records de l'exercice et en seconde courbe (orange) du graphique.
- **Plateau** : une séance « progresse » quand son meilleur 1RM estimé bat toutes les séances précédentes (séance d'exemple comprise) ;
  après **3 séances d'affilée sans progrès**, une carte « Stagnation depuis N séances » apparaît en séance (exercice déplié) et dans
  l'historique de l'exercice, avec trois pistes : changer de plage de reps 2 à 3 semaines, repartir à −10 % de la dernière charge
  (arrondi à l'incrément de la machine), passer à une variante avec « Remplacer ». La suggestion de progression reste affichée à côté.
- **Décharge** (simple conseil, carte sur l'accueil) : après **6 semaines complètes d'affilée** avec au moins une série de travail,
  ou quand **3 exercices ou plus** (pratiqués ces 3 dernières semaines) sont en plateau. Une semaine sans séance, ou une semaine
  avec moins de 60 % des séries de la moyenne des 4 précédentes (décharge faite), remet le compteur à zéro. « Compris » masque
  la carte jusqu'à la semaine suivante.

### Fréquence cardiaque (V2.1)

- **FC en direct (source principale)** : la montre diffuse sa FC en Bluetooth LE (profil standard *Heart Rate*,
  service 0x180D). Config → Montre cardio : choisir la montre parmi les appareils qui diffusent une FC. En séance, GymBro la
  **recherche** (filtre 0x180D, reconnue par adresse ou par nom : l'adresse de diffusion est aléatoire et peut changer) et
  s'y connecte dès que la diffusion est active, puis se reconnecte après chaque coupure. Un **service au premier plan**
  (type *connected device*) garde la liaison écran éteint ; chaque mesure est écrite au fil de l'eau dans un fichier par
  séance (rien n'est perdu si l'app est tuée). Notification « Séance en cours » avec la FC, *Live Update* Android 16 ; retirée explicitement à l'arrêt (2.9.1 : elle pouvait être republiée juste après l'abandon de la séance et rester affichée).
- **En séance** : bandeau FC + zone sous le titre ; barre de repos : « récupéré » sous 60 % de la FC max (indication seule).
- **Complément Health Connect** (après séance) : la FC écrite par Mi Fitness comble les trous de la courbe en direct (le
  direct est prioritaire), et ses calories actives remplacent l'estimation. **Une seule source** est retenue (l'app qui a
  écrit la FC, sinon Mi Fitness) : Health Connect contient aussi Fitbit, le podomètre du téléphone…, qu'il ne faut pas
  additionner. Rattrapage en tâche de fond (5 min après la séance puis toutes les 30, 60, 90… min, 7 essais) et à l'ouverture
  du bilan ; une séance de plus de 24 h n'est plus modifiée. Mi Fitness n'écrit une FC détaillée que pendant une diffusion
  ou un entraînement lancé sur la montre.
- **FC max** : Tanaka (208 − 0,7 × âge) à partir de l'année de naissance (Config → Profil), ou valeur personnalisée.
  Si une séance la dépasse (moyenne sur 5 s, les pics parasites sont ignorés), le bilan propose de l'ajuster.
- **Zones** : Z1 50-60 %, Z2 60-70 %, Z3 70-80 %, Z4 80-90 %, Z5 90-100 % de la FC max. « Intensité de travail » = zone 3 et +.
- **Calories estimées** (sans données de la montre) : formule de Keytel (FC, poids, âge, sexe), sommée par tranche de 5 s.
- **Calories de la montre** (calories actives écrites par Mi Fitness dans Health Connect) : retenues seulement si elles valent
  **au moins 50 % de l'estimation** par la FC. Hors entraînement lancé sur la montre, Mi Fitness n'écrit que quelques calories
  éparses (2 kcal pour une séance d'une heure observés) : l'estimation est alors gardée et la synchro réessaie jusqu'à 24 h.
  Les séances déjà enregistrées avec un chiffre trop faible sont corrigées à l'ouverture de leur bilan (2.3.1).
- **Bilan** : courbe avec bandes de zones et repères des séries validées, temps par zone, **pic de FC par série** (maximum
  dans la minute qui précède la validation). **Semaine** : séances mesurées, FC moyenne, temps en zone 3 et +, kcal.
  **Nutrition** : dépense des séances du jour et apport net.

### Bibliothèque d'exercices (V2.8 – V2.10)

- **Fiche exercice** : illustration animée (fondu enchaîné entre les images dans l'ordre de la bibliothèque, 0,9 s
  de maintien, 0,35 s de fondu ; image de maintien fixe ; **toucher = pause**), dans une carte sombre de 340 dp de
  haut au plus, puis la mention « Illustration générée par IA, à titre indicatif… », le nom de la bibliothèque, le
  groupe, le matériel (traduit), les muscles principaux / secondaires / « sollicite aussi », niveau, type de mouvement,
  catégorie, impact, lieux, vue, « un côté à la fois » et « échauffement » quand c'est le cas, la différence d'une
  variante (texte anglais de la bibliothèque) ; enfin **mes repères** (réglages, note de charge) et **ma photo** de la
  machine. Aucune consigne d'exécution n'est inventée.
- Accès : icône **(i)** sur chaque exercice de la séance en cours et dans l'historique d'un exercice ; vignette du
  catalogue (qui affiche aussi « sans illustration ») ; « Voir la fiche » dans « Modifier l'exercice ».
- **Modifier l'exercice → Illustration** : voir la fiche, **changer** (liste des 459 entrées avec vignette, recherche
  par mots du nom ou d'un alias, sans accents ni casse, (i) pour prévisualiser) ou **retirer**.
- **Config → Bibliothèque d'exercices** (V2.10) : les 459 entrées avec vignette, recherche par nom ou alias, filtres
  **muscle principal**, **matériel** (dont « Poids du corps ») et **lieu** (salle, machines, maison, poids du corps),
  combinables, « Effacer » pour les retirer. Toucher une entrée ouvre sa fiche. Les entrées déjà reliées à un exercice
  du catalogue (archivés compris) sont marquées « Déjà dans mon catalogue » (avec son nom s'il diffère) ; les autres
  ont un **+** qui crée l'exercice : nom anglais de la bibliothèque, groupe déduit du 1er muscle principal, type
  déduit (machine cardio → Cardio 1 × 20 min ; image de maintien → Durée 3 × 30 s, par côté si unilatéral ; sans
  matériel → Poids du corps ; sinon Charge + reps, 3 × 10–12), relié à son illustration. Le message de confirmation
  propose **Modifier** pour ajuster aussitôt.
- Images décodées à la taille affichée (sous-échantillonnage) et gardées en cache : les listes n'affichent que des
  vignettes fixes, l'animation n'existe que sur la fiche. Libellés français dans `LibraryLabels` (textes de l'app
  écrits en Kotlin, comme le reste de l'interface) ; un test vérifie que chaque valeur de la bibliothèque a son libellé.

### Raccourcis et widget (V2.6)

- **Raccourcis** (appui long sur l'icône) : **Démarrer la séance** lance directement la séance proposée par la rotation
  (ou rouvre la séance en cours), **Ajouter un repas** ouvre la recherche et **Scanner** le scanner, sur le repas de
  l'heure (petit-déjeuner avant 11 h, déjeuner avant 15 h, collation avant 18 h 30, dîner ensuite). Raccourcis
  dynamiques publiés au démarrage : un raccourci statique écrit le nom du package en dur et casserait la variante debug.
  « Retour » ramène à l'accueil.
- **Widget 4 × 2** (redimensionnable) : prochaine séance (ou séance en cours ; son nom seul, sans le code qui le répète, 2.7.2), séances de la semaine / objectif,
  bouton **Démarrer** (même action que le raccourci) ; anneaux **kcal** et **protéines** du jour (mêmes calculs et
  couleurs que le journal), qui ouvrent le journal nutrition. Thème sombre fixe, lisible sur tous les fonds d'écran.
  Colonne de texte de 135 dp ; les anneaux se partagent tout le reste, image ajustée à leur place (2.9.0). Leur taille
  n'est plus calculée d'après celle que déclare le lanceur : certains déclarent la taille minimale du widget, ce qui
  les laissait petits en 2.7.x. Le bouton garde sa taille.
  Coins libres (2.7.2) : petit **G** bleu en haut à droite ; en bas à droite l'heure du dernier dessin
  (« Màj 10:35 », avec la date si ce n'est pas aujourd'hui) et **↻**, qui redessine le widget depuis la base.
  Toucher le reste de la carte (fond, logo) ouvre l'app sur l'**Accueil** (2.9.1).
- **Données suivies en direct** (2.9.1) : la session Glance survit à son premier dessin, et une mise à jour ne fait que
  la recomposer. Les données sont donc observées dans la composition elle-même : lues une seule fois, une séance
  abandonnée restait affichée « Reprendre », même après ↻.
- **Mise à jour** sans réveil périodique : redessiné quand ses données changent pendant que l'app tourne (repas, séance,
  pesée, profil ; regroupé sur 1 s) et juste après minuit (WorkManager) pour la nouvelle journée.

### Nutrition (V1.1)

- **Écran Profil** : le formulaire n'apparaît qu'une fois le profil enregistré relu, et « Enregistrer » reste grisé
  jusque-là (2.9.2). Avant, une saisie faite pendant cette lecture (lente au premier lancement, base occupée par Ciqual
  et la bibliothèque) était effacée, et « Enregistrer » pouvait écrire un profil vide.
- **Objectif protéines** = facteur du profil (1,8 g/kg par défaut, modifiable de 0,8 à 3,0) × **dernier poids saisi**.
- **Anneaux du jour** (V2.5) : deux grands anneaux kcal et protéines (consommé / objectif, reste ; kcal en orange au-delà de +10 %,
  protéines en vert une fois l'objectif atteint), deux petits pour glucides et lipides face à leur cible, puis la répartition
  énergétique (4 / 4 / 9 kcal par g). L'accueil garde la jauge protéines.
- **Repas** : petit-déjeuner, déjeuner, collation, dîner, plus des **collations supplémentaires** horodatées.
- **Whey** : 1 dose par jour (2 si le conseil protéines le demande, V2.2) (valeurs de l'étiquette saisies dans le profil). Moment conseillé : **juste après la séance** les jours d'entraînement, au petit-déjeuner ou en collation les jours de repos.
- **Créatine** : 5 g/jour par défaut, case « prise » avec série de jours consécutifs. **Rappel** quotidien à l'heure choisie (20:00 par défaut), **non envoyé si la prise est déjà notée**, avec bouton « C'est pris » dans la notification ; reprogrammé après redémarrage du téléphone.
- **Code-barres** : scan caméra (CameraX + ML Kit embarqué, hors ligne) puis Open Food Facts ; chaque produit trouvé est mis en cache et reste disponible hors ligne. Produit inconnu, sans protéines, ou réseau absent : création depuis l'étiquette ou recherche Ciqual.
- **Ciqual** : table ANSES 2025 (3 484 aliments) embarquée. Quand un aliment Ciqual est utilisé, il rejoint « Mes aliments » (récents, favoris).

### Objectif calories et conseil protéines (V2.2)

- **Objectif** (Profil → Objectif calories) : prise de masse (+10 %), maintien, sèche (−20 %) ; aucun objectif choisi = pas de suivi des calories.
- **Jour de repos** = métabolisme de base Mifflin-St Jeor (poids, taille, âge, sexe) × 1,4 (vie quotidienne hors séances) × ajustement de l'objectif,
  arrondi à 10 kcal. Valeur modifiable (± 50 kcal), « Revenir à la valeur conseillée » ; changer d'objectif efface la valeur personnalisée.
- **Jour d'entraînement** : objectif repos **+ calories des séances du jour**, métabolisme de base retiré (2.3.2) : l'estimation par
  la FC (Keytel) est une dépense brute, comparable aux « kcal totales » de Mi Fitness, alors que l'objectif repos contient déjà
  le métabolisme de base ; il est donc soustrait sur la durée mesurée (Mifflin-St Jeor, ou 24 kcal/kg/jour sans la taille).
  Les calories actives de la montre, déjà nettes, sont ajoutées telles quelles. Le bilan de séance garde le chiffre brut.
- **Cibles glucides et lipides** (V2.5), méthode de la nutrition du sport (ACSM / ISSN : lipides 20–35 % des kcal) :
  lipides = **30 % de l'objectif kcal** (prise de masse, maintien) ou **25 %** (sèche, pour garder des glucides à l'entraînement),
  jamais sous **0,6 g/kg** ; glucides = ce qui reste après protéines et lipides. Les kcal des séances vont donc aux glucides.
  Exemple : 2 300 kcal, 160 g de protéines → lipides 77 g, glucides 243 g. Sans objectif kcal ou sans pesée : grammes seuls.
- **Conseil protéines** (aujourd'hui seulement) : manque = objectif − consommé ; repas à venir = repas principaux pas encore saisis et dont l'heure
  n'est pas passée (petit-déj. 11 h, déjeuner 15 h, collation 18 h 30, dîner 22 h 30), estimés par leur moyenne des 14 derniers jours
  (repas présent au moins 3 jours, whey et collations supplémentaires exclues). Doses de whey = (manque − repas à venir − 5 g de tolérance) ÷ protéines
  par dose, arrondi au supérieur, **2 doses par jour au maximum** ; au-delà, le reste est à trouver dans l'alimentation.
- **Bilan de la semaine** (Historique → Semaine) : jours saisis, protéines moyennes et jours à l'objectif (poids actuel), kcal moyennes et jours
  dans l'objectif (± 10 %, séances comprises), jours avec whey et créatine.

Source des données de composition : *Anses. 2025. Table de composition nutritionnelle des aliments Ciqual* —
[ciqual.anses.fr](https://ciqual.anses.fr/), Licence Ouverte / Etalab 2.0. Produits scannés : [Open Food Facts](https://world.openfoodfacts.org/) (ODbL).

---

## Données initiales (seed au premier lancement)

Une **séance d'amorce** (`isSeed = true`, modèle B, terminée la veille de l'installation) contient une série par exercice dont la charge est connue. Elle sert à :
- pré-remplir les charges actuelles comme « dernière fois » ;
- positionner la rotation (dernière séance = **B** → séance proposée : **C**) ;
- servir de référence de base pour les records (pas de faux records à la première séance).

Elle est masquée de l'historique et des graphiques et ne déclenche aucune suggestion de progression.

| Exercice | Type | Groupe | Charge actuelle | Cible par défaut |
|---|---|---|---|---|
| Rameur | CARDIO | CARDIO | — | 5 min |
| Shoulder press | WEIGHTED | EPAULES | 20 kg | 3×10 |
| Chest press | WEIGHTED | PECTORAUX | 15 kg | 3×10 |
| Pec machine | WEIGHTED | PECTORAUX | 10 kg | 3×10 |
| Pull down | WEIGHTED | DOS | 25 kg | 3×12 |
| Pull down prise neutre | WEIGHTED | DOS | 22,5 kg | 3×12 |
| Rowing machine | WEIGHTED | DOS | 20 kg | 3×10 |
| Élévations latérales | WEIGHTED | EPAULES | 6 kg (incrément 1 kg) | 2×15 |
| Abdominal crunch machine | WEIGHTED | ABDOS | 15 kg | 3×15 |
| Chaise romaine | BODYWEIGHT | ABDOS | poids de corps | 3×15 |
| Planche latérale | TIMED (par côté) | ABDOS | — | 3×25 s/côté |
| Curl biceps | WEIGHTED | BRAS | — | 2×12 |
| Leg curl | WEIGHTED | JAMBES | 15 kg | 3×12 |
| Abducteurs machine | WEIGHTED | JAMBES | 20 kg | 3×12 |
| Hack squat | WEIGHTED | JAMBES | 10 kg (`loadNote` : chariot vide + X kg) | 3×12 |
| Mollets debout | WEIGHTED | JAMBES | — | 3×15 |

Modèles (Rameur : 5 min par défaut dans tous les modèles, modifiable) :

| Code | Nom | Rotation | Exercices |
|---|---|---|---|
| A | Poussée + tronc | 1 | Rameur, Shoulder press 3×10, Chest press 3×10, Élévations latérales 2×15, Abdominal crunch machine 3×15, Chaise romaine 3×12 |
| B | Tirage + tronc | 2 | Rameur, Pull down 3×12, Rowing machine 3×12, Chaise romaine 3×12, Planche latérale 3×25 s/côté, Curl biceps 2×12 *(optionnel)* |
| C | Mixte + tronc dominant | 3 | Rameur, Chest press 3×10, Pull down prise neutre 3×12, Abdominal crunch machine 3×15, Chaise romaine 3×12 |
| BAS | Bas du corps | hors rotation | Rameur, Hack squat 3×12, Leg curl 3×12, Abducteurs 3×12, Mollets 3×15 *(optionnel)* |

---

## Sauvegarde (export / import JSON)

### Sauvegarde automatique (V1.2)

- **Réglages → Sauvegarde automatique → Choisir l'emplacement…** : le sélecteur Android (Storage Access Framework) s'ouvre ;
  choisir **Google Drive**, le dossier voulu, et valider `gymbro-sauvegarde.json` (un fichier `forme-sauvegarde.json` déjà choisi reste utilisé tel quel). L'app garde un droit d'écriture
  **persistant sur ce seul fichier** : aucun compte Google dans l'app, aucun projet Google Cloud, aucune clé.
- Le fichier est **réécrit en place** après chaque **séance terminée**, **une fois par jour** (WorkManager) et via
  « Sauvegarder maintenant ». L'app Drive se charge de l'envoi (différé si hors ligne) et conserve les **versions
  précédentes** (⋮ → Gérer les versions).
- Les **3 dernières sauvegardes** sont aussi gardées dans le stockage privé de l'app (`files/auto-backups/`).
- **Vérification** (V2.6) : après écriture, le fichier cible et la copie locale sont **relus**, comparés octet par octet
  à ce qui a été écrit, puis décodés comme pour une restauration (sans toucher à la base). « Relue et vérifiée »
  s'affiche dans les réglages ; un fichier tronqué, différent ou illisible compte comme un échec.
- Dernière sauvegarde réussie et erreur éventuelle affichées dans les réglages ; un échec de la sauvegarde en
  arrière-plan (écriture ou vérification) envoie aussi une **notification**, effacée à la sauvegarde réussie suivante.
  Si le fichier est supprimé, déplacé ou si le droit est perdu : message clair, il suffit de rechoisir l'emplacement.
- **Restaurer** : « Importer une sauvegarde… » puis choisir le fichier dans Drive (même import que ci-dessous).

### Export / import manuel

- Export / import d'un fichier JSON complet via le **Storage Access Framework** (choix libre de l'emplacement).
- Format versionné : `{ "schemaVersion": 6, "exportedAt": "…", "sport": { …, "heart" }, "bodyWeight": [ … ], "nutrition": { "profile", "foods", "meals", "creatine" } }`.
  La FC des séances (courbe + résumé) est incluse depuis la v4, l'objectif calories depuis la v5, le lien vers la bibliothèque d'exercices (`libraryId`) depuis la v6. Les fichiers **v1** à **v5** restent importables (champs absents = valeurs par défaut). Les tables Ciqual et bibliothèque d'exercices ne sont pas exportées (elles sont dans l'APK).
- **L'import remplace toutes les données** (transaction unique, après confirmation). Une sauvegarde de sécurité est exportée automatiquement juste avant.
- Les **photos** des machines (copiées dans le stockage interne de l'app) ne sont **pas** incluses dans le JSON.

---

## Prérequis (Windows)

1. **JDK 17 ou 21** (ex. Eclipse Temurin) — définir `JAVA_HOME`.
2. **Android SDK** : soit Android Studio, soit les *command-line tools* seuls :
   - télécharger « Command line tools only » sur <https://developer.android.com/studio#command-line-tools-only> ;
   - extraire dans `C:\Android\sdk\cmdline-tools\latest\` ;
   - définir `ANDROID_HOME=C:\Android\sdk` et ajouter `%ANDROID_HOME%\platform-tools` au `PATH` ;
   - installer les paquets :
     ```bat
     sdkmanager "platform-tools" "platforms;android-<targetSdk>" "build-tools;<version>"
     sdkmanager --licenses
     ```
3. Sur le téléphone : activer les **options développeur** et le **débogage USB**.

Gradle n'a pas besoin d'être installé : le wrapper `gradlew` est fourni.

---

## Signature de l'APK

La configuration `release` lit un fichier `keystore.properties` à la racine, **exclu du dépôt** (`.gitignore`).

1. Générer la clé (une seule fois) :
   ```bat
   keytool -genkeypair -v -keystore forme-release.jks -alias forme -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Créer `keystore.properties` (modèle : `keystore.properties.example`) :
   ```properties
   storeFile=C:/chemin/vers/forme-release.jks
   storePassword=********
   keyAlias=forme
   keyPassword=********
   ```

> ⚠️ **Sauvegarder le `.jks` et ses mots de passe hors du poste** (gestionnaire de mots de passe + copie hors ligne).
> Sans cette clé, impossible de mettre à jour l'app installée sans la désinstaller (et donc perdre les données locales).
> Son empreinte SHA-1 serait nécessaire pour une future intégration Drive complète (client OAuth Google) :
> `keytool -list -v -keystore forme-release.jks -alias forme`

---

## Build

```bat
gradlew assembleRelease
```

APK produit : `app\build\outputs\apk\release\app-release.apk`

Tests :

```bat
gradlew testDebugUnitTest       :: règles métier, sauvegarde, Room (Robolectric), parcours UI — sans téléphone
gradlew connectedAndroidTest    :: tests Room instrumentés (DAO, migrations 1 → 2 → 3 → 4 → 5) sur le téléphone branché
gradlew lintDebug               :: analyse statique Android
```

Couverture : règle de progression, rotation (ajout / archivage / réordonnancement), records et volume,
moyenne mobile 7 jours, pré-remplissage, bilan texte, export/import JSON (aller-retour sans perte, en mémoire
et via la base), seed, DAO et repositories, migrations Room 1 → 5, supersets (normalisation), statistiques hebdomadaires, remplacement d'exercice, fréquence cardiaque (décodage Bluetooth, zones, Keytel, fusion direct/Health Connect, source unique, pics par série, synchro, enregistreur), objectif calories (Mifflin-St Jeor), conseil protéines / whey, bilan nutrition de la semaine, 1RM estimé, plateau, conseil de décharge, fichier d'analyse IA et partage.

Les tests de fumée (`AppSmokeTest`, `RecapSmokeTest`, `FeaturesSmokeTest`, …) écrivent des captures d'écran dans `app/build/smoke-screens/`.

---

## CI GitHub Actions

`.github/workflows/android.yml` lance à chaque pull request (et à chaque push sur `main`) :
`testDebugUnitTest`, `lintDebug` et `assembleRelease`.

- **APK de la PR** : onglet *Actions* → le run → artefact `forme-<version>-apk` (conservé 30 jours).
- **Release** : quand une nouvelle `versionName` arrive sur `main`, une release GitHub `v<version>` est publiée
  avec l'APK signé (une seule fois par version : penser à monter `versionCode`/`versionName`).
  Sur le téléphone : page *Releases* du dépôt → télécharger l'APK → installer (mise à jour sans perte de données,
  même clé que les builds locaux).

Signature : quatre secrets de dépôt (*Settings → Secrets and variables → Actions*) :

| Secret | Valeur |
|---|---|
| `FORME_KEYSTORE_BASE64` | le `.jks` encodé en base64 |
| `FORME_STORE_PASSWORD` | mot de passe du keystore |
| `FORME_KEY_ALIAS` | `forme` |
| `FORME_KEY_PASSWORD` | mot de passe de la clé |

Encodage du keystore sous Windows (copié dans le presse-papiers) :

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\chemin\vers\forme-release.jks")) | Set-Clipboard
```

Sans ces secrets, l'APK est construit **non signé** (non installable) et aucune release n'est publiée.
Le keystore n'est écrit que le temps du build sur le runner, puis supprimé.

---

## Installation

```bat
adb devices
adb install -r app\build\outputs\apk\release\app-release.apk
```

`-r` réinstalle en conservant les données (même clé de signature obligatoire).

---

## Restauration d'une sauvegarde JSON

1. Copier le fichier `.json` sur le téléphone (ou le rendre accessible via le sélecteur de fichiers : stockage interne, Drive…).
2. Dans l'app : **Configuration → Sauvegarde → Importer**, choisir le fichier.
3. Confirmer : les données actuelles sont sauvegardées automatiquement puis **remplacées** par celles du fichier.

La copie de sécurité automatique (5 dernières conservées) se trouve dans
`Android/data/fr.olivier.forme/files/backups/` ; pour la récupérer sur le PC :

```bat
adb pull /sdcard/Android/data/fr.olivier.forme/files/backups/
```

---

## Notes d'implémentation

- **Séance en cours persistante** : chaque série validée est écrite immédiatement en base ; une séance sans
  `endedAt` est reprise automatiquement au lancement. Seules les valeurs en cours de saisie (pas encore validées) sont perdues si Android tue l'app.
- **Timer de repos** : notification avec compte à rebours (visible écran verrouillé) + alarme exacte
  (`USE_EXACT_ALARM`) qui sonne et vibre en fin de repos, même app fermée. La permission de notification est demandée au premier lancement d'une séance (Android 13+).
- **Pré-remplissage** : la série suivante reprend la série que tu viens de faire (si tu as changé la charge, elle est conservée),
  sinon la même série de la dernière fois. Toucher une valeur permet de la saisir au clavier.
- **Records** : un record doit battre strictement la meilleure valeur précédente ; une première fois n'est pas un record.
- **Variante de build debug** : `fr.olivier.forme.debug`, installable à côté de la release sans conflit de données.
- **Permissions demandées** : notifications (timer de repos, rappel créatine), caméra (uniquement à l'ouverture du scanner),
  Internet (uniquement pour Open Food Facts), alarmes exactes (fin de repos), démarrage (reprogrammer le rappel créatine).
- **APK release** limité à l'architecture `arm64-v8a` (tous les téléphones 64 bits récents) pour économiser ~15 Mo
  de code natif ML Kit ; le build debug garde toutes les architectures (émulateurs).
