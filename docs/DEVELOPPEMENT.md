# Développement

Compiler, signer, tester et publier GymFlemme. Le fonctionnement de l'app est décrit dans
[ARCHITECTURE.md](ARCHITECTURE.md).

Méthode de travail du projet : proposition → validation → implémentation par étapes ; commits atomiques ;
vérifications locales complètes (`gradlew testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug lintPlayDebug
assembleGithubRelease assemblePlayRelease`) avant chaque push ; une PR par
version, fusionnée sur `main`, qui publie la release.

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

Deux variantes depuis V2.28 (même code, même identifiant) : **`github`**, qui se met à jour depuis le dépôt de
versions, et **`play`**, sans code de mise à jour ni permission d'installer (voir [publication.md](publication.md)).

```bat
gradlew assembleGithubRelease
gradlew assemblePlayRelease
```

APK produits : `app\build\outputs\apk\github\release\app-github-release.apk` et `…\play\release\app-play-release.apk`

Tests :

```bat
gradlew testGithubDebugUnitTest testPlayDebugUnitTest  :: règles métier, sauvegarde, Room (Robolectric), parcours UI — sans téléphone
gradlew connectedAndroidTest    :: tests Room instrumentés (DAO, migrations 1 → 2 → 3 → 4 → 5) sur le téléphone branché
gradlew lintGithubDebug lintPlayDebug                  :: analyse statique Android
```

Couverture : règle de progression, rotation (ajout / archivage / réordonnancement), records et volume,
moyenne mobile 7 jours, pré-remplissage, bilan texte, export/import JSON (aller-retour sans perte, en mémoire
et via la base), seed, DAO et repositories, migrations Room 1 → 5, supersets (normalisation), statistiques hebdomadaires, remplacement d'exercice, fréquence cardiaque (décodage Bluetooth, zones, Keytel, fusion direct/Health Connect, source unique, pics par série, synchro, enregistreur), objectif calories (Mifflin-St Jeor), conseil protéines / whey, bilan nutrition de la semaine, 1RM estimé, plateau, conseil de décharge, fichier d'analyse IA et partage.

Les tests de fumée (`AppSmokeTest`, `RecapSmokeTest`, `FeaturesSmokeTest`, …) écrivent des captures d'écran dans `app/build/smoke-screens/`.

---

## CI GitHub Actions

`.github/workflows/android.yml` lance à chaque pull request (et à chaque push sur `main`) :
les tests, le lint et la compilation release des **deux variantes** (`github` et `play`) ; la variante `play` vérifie
qu'elle ne contient pas la permission d'installer des apps. Depuis V2.28, **la publication se fait par un tag**
(`.github/workflows/publish.yml`, procédure dans [publication.md](publication.md)).
Runner **épinglé sur `ubuntu-24.04`** (`ubuntu-latest` passe à Ubuntu 26 à partir du 19 octobre 2026) et actions en
versions **Node 24** (`checkout`/`setup-java`/`setup-gradle` v5, `upload-artifact` v7, `download-artifact` v8) : à
monter volontairement, ensemble, quand GitHub l'annonce.

- **APK de la PR** : onglet *Actions* → le run → artefact `gymflemme-<version>-apk` (`gymbro-…` de 2.11.0 à 2.13.0, `forme-…` avant) (conservé 7 jours, pour rester dans le quota de stockage GitHub Actions).
- **Release** : jusqu'à la 2.27, publiée dans ce dépôt au merge sur `main` ; depuis la 2.28, par un tag `v*` dans
  `ollivv-hash/gymflemme-releases` (voir [publication.md](publication.md)).

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

Sans ces secrets, les APK de la CI sont construits **non signés** (non installables) ; le workflow de publication, lui, s'arrête.
Le keystore n'est écrit que le temps du build sur le runner, puis supprimé.

---

## Installation

```bat
adb devices
adb install -r app\build\outputs\apk\github\release\app-github-release.apk
```

`-r` réinstalle en conservant les données (même clé de signature obligatoire).

---
