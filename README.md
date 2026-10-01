# Forme — App Sport & Nutrition (Android, usage personnel)

Application Android native de suivi des séances en salle (module **Sport**) puis de nutrition (module **Nutrition**, V1.1).
Usage strictement personnel : un utilisateur, un téléphone, APK signé installé via `adb install`, sans Play Store.

> **Statut : V1.0 en cours de conception.** L'architecture ci-dessous est validée ; le développement démarre une fois l'environnement de build prêt.
> Les sections « Build » et « Installation » décrivent la procédure cible de la V1.0.

- Package : `fr.olivier.forme` — nom d'app provisoire : **Forme**
- Interface en français, code (identifiants, commentaires) en anglais
- **Hors ligne total** : aucune fonction V1.0 ne dépend du réseau ; aucune API IA, aucun service payant, aucune clé d'API dans le code

---

## Feuille de route

| Version | Contenu |
|---|---|
| **V1.0** | Socle technique + module Sport (séances, timer de repos, historique, records, poids, sauvegarde JSON) |
| V1.1 | Profil, repas (code-barres Open Food Facts, table Ciqual embarquée, favoris), jauge protéines (1,6 g/kg/j), dose whey, suivi créatine |
| V1.2 | Synchronisation Google Drive (backup JSON + exports Markdown), API Drive v3, scope `drive.file` |
| V2.x | Partage des bilans vers l'app Claude, Health Connect (Xiaomi Watch S4 via Mi Fitness), fréquence cardiaque BLE (profil Heart Rate standard) |

Hors périmètre V1.0 : Nutrition, Drive, Health Connect, BLE, partage vers Claude, rappels créatine, widgets, Wear OS, multi-utilisateur, analyse photo.

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
| AGP, Kotlin, plugin Compose Compiler | Socle |
| KSP | Génération de code Room et Hilt (remplace kapt, déprécié) |
| Compose BOM, Material 3, `activity-compose`, `material-icons-extended` | UI |
| `lifecycle-runtime-compose`, `lifecycle-viewmodel-compose` | `collectAsStateWithLifecycle`, ViewModels |
| `navigation-compose` | Navigation (routes typées via kotlinx.serialization) |
| Hilt + `hilt-navigation-compose` | Injection de dépendances, `hiltViewModel()` |
| Room (runtime, ktx, compiler, `room-testing`) | Persistance ; `room-testing` pour valider les migrations |
| `datastore-preferences` | Réglages |
| `kotlinx-serialization-json` | Export / import JSON, routes typées ; maintenu par JetBrains, sans réflexion |
| `kotlinx-coroutines` | Asynchronisme |
| `sh.calvin.reorderable` | Glisser-déposer dans une `LazyColumn` (absent de Compose) ; petite lib Apache 2, maintenue. **Seule dépendance tierce.** |
| Tests : JUnit 4, `kotlinx-coroutines-test`, Turbine, `androidx.test` | Tests unitaires et instrumentés |

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
        ├── test/                # tests unitaires JVM
        └── androidTest/         # tests DAO + migration
```

V1.1 ajoutera `feature/nutrition/` et une migration Room 1 → 2 ; V1.2 ajoutera `core/sync/`.

---

## Modèle de données (Room, version 1)

Unités : charges en **kg** (`Double`, pas de 0,5), durées en secondes, distances en mètres. `Instant` ↔ epoch millis, `LocalDate` ↔ epochDay. Les enums sont stockées en texte.

| Table | Colonnes principales | Remarques |
|---|---|---|
| `exercise` | id, name, muscleGroup, type, perSide, loadIncrementKg (2,5), **defaultTargetSets, defaultRepsMin, defaultRepsMax, defaultDurationSec**, settingsNote, loadNote, photoUri, archived | Cible par défaut utilisée à l'ajout de l'exercice dans un modèle ou une séance libre. Archivé = masqué des choix, historique conservé. |
| `workout_template` | id, name, code, orderInRotation (nullable = hors rotation), archived | |
| `template_exercise` | id, templateId, exerciseId, position, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, optional, restSec (nullable → réglage par défaut) | Le modèle fait foi sur la cible du catalogue. |
| `session` | id, templateId (nullable, SET NULL), templateNameSnapshot, startedAt, endedAt (null = en cours), feeling 1–5, notes, isSeed | La séance en cours est persistée en base → reprise après kill du process. |
| `session_exercise` | id, sessionId, exerciseId, position, status (PENDING/DONE/SKIPPED), copie des cibles (sets, reps min/max, durée, repos, optionnel) | Copie figée au démarrage : modifier ou archiver un modèle n'altère **jamais** une séance passée. Porte aussi réordonnancement / saut / ajout en cours de séance. |
| `set_entry` | id, sessionId, exerciseId, setIndex, loadKg, reps, durationSec, rir (0–5), isWarmup, completedAt | |
| `cardio_entry` | id, sessionId, exerciseId, durationSec, distanceM, pacePer500mSec, caloriesDisplayed | Allure calculée depuis durée/distance, modifiable. |
| `body_weight_entry` | id, date (unique), weightKg | Une pesée par jour (une nouvelle saisie remplace). |

Les exercices ne sont jamais supprimés (clés étrangères `RESTRICT`), seulement archivés.

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

- Export / import d'un fichier JSON complet via le **Storage Access Framework** (choix libre de l'emplacement).
- Format versionné : `{ "schemaVersion": 1, "exportedAt": "…", "sport": { … }, "bodyWeight": [ … ] }` — la section `nutrition` s'ajoutera en V1.1, l'ancien format restant importable.
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
> Son empreinte SHA-1 sera aussi nécessaire pour créer le client OAuth Google en V1.2 :
> `keytool -list -v -keystore forme-release.jks -alias forme`

---

## Build

```bat
gradlew assembleRelease
```

APK produit : `app\build\outputs\apk\release\app-release.apk`

Tests :

```bat
gradlew test                    :: tests unitaires
gradlew connectedAndroidTest    :: tests instrumentés (téléphone ou émulateur branché)
```

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
