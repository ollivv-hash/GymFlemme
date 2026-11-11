# Publier une version

Depuis la V2.28, une version se publie en **poussant un tag** `vX.Y.Z` sur ce dépôt de code. Le workflow
`.github/workflows/publish.yml` :

1. lance les tests ;
2. compile la variante `github` (`assembleGithubRelease`), signée avec la clé stockée dans les secrets du dépôt ;
3. calcule le SHA-256 de l'APK et écrit `update.json` (`versionCode` et `versionName` lus dans `app/build.gradle.kts`,
   notes tirées de la ligne de la version dans `docs/CHANGELOG.md`) ; le workflow échoue si le tag ne correspond pas au
   `versionName` ;
4. crée la release `vX.Y.Z` dans le **dépôt de versions** `ollivv-hash/gymflemme-releases`, avec trois fichiers :
   `gymflemme-X.Y.Z.apk`, `gymflemme-X.Y.Z.apk.sha256` et `update.json`.

L'app (variante `github`) lit la dernière release de ce dépôt, au lancement au plus une fois par 24 h, ou par
Réglages → À propos → « Rechercher une mise à jour ».

Le merge d'une PR sur `main` ne publie plus rien : la CI (`android.yml`) teste et compile les deux variantes, et
l'APK de chaque run reste téléchargeable comme artefact pendant 7 jours.

## Une seule fois : préparer GitHub

### 1. Créer le dépôt de versions

1. github.com → **New repository** → nom `gymflemme-releases`, **Public**, cocher **Add a README file** (le dépôt
   doit avoir au moins un commit pour recevoir des tags), sans licence ni `.gitignore`.
2. Dans le README, une phrase suffit, par exemple : « Versions publiées de GymFlemme. Le code est dans
   [ollivv-hash/GymFlemme](https://github.com/ollivv-hash/GymFlemme). »
3. Rien d'autre : ce dépôt ne contient que les publications (*Releases*).

### 2. Créer le jeton à accès restreint

1. github.com → photo de profil → **Settings** → **Developer settings** → **Personal access tokens** →
   **Fine-grained tokens** → **Generate new token**.
2. **Token name** : `gymflemme-publish`. **Expiration** : 1 an (noter la date pour le renouveler).
3. **Resource owner** : ton compte. **Repository access** : **Only select repositories** → `gymflemme-releases` seul.
4. **Permissions** → **Repository permissions** → **Contents : Read and write** (le reste à *No access* ; *Metadata :
   Read-only* est ajouté d'office).
5. **Generate token**, copier la valeur (elle ne sera plus affichée).

### 3. Créer les secrets du dépôt de code

github.com → dépôt **GymFlemme** → **Settings** → **Secrets and variables** → **Actions** → **New repository secret** :

| Secret | Valeur | Déjà présent |
|---|---|---|
| `FORME_KEYSTORE_BASE64` | le fichier `.jks` encodé en base64 | oui |
| `FORME_STORE_PASSWORD` | mot de passe du keystore | oui |
| `FORME_KEY_ALIAS` | alias de la clé (`forme`) | oui |
| `FORME_KEY_PASSWORD` | mot de passe de la clé | oui |
| `GYMFLEMME_RELEASES_TOKEN` | le jeton de l'étape 2 | **à créer** |

Les quatre premiers servent déjà à la CI : ne les recrée que si tu changes de clé (ce qui est à éviter, voir plus bas).
GitHub masque la valeur des secrets dans les journaux ; le workflow n'en affiche aucun. Il affiche l'**empreinte
SHA-256 du certificat** de signature (publique), pour vérifier que c'est la bonne clé.

Encoder la clé en base64 :

```powershell
# Windows PowerShell (copié dans le presse-papiers)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\chemin\vers\forme-release.jks")) | Set-Clipboard
```

```bash
# Linux (une seule ligne, sans retour à la ligne)
base64 -w0 forme-release.jks > forme-release.jks.b64
```

## La même clé, toujours

Android n'accepte une mise à jour que si l'APK est signé avec **la même clé** que l'app installée. Le workflow signe
avec les secrets `FORME_*`, les mêmes que la CI depuis la V2.2 : ce sont ceux de l'APK installé sur ton téléphone.
Pour le vérifier, compare l'empreinte affichée dans le journal du workflow (« Signing certificate SHA-256 digest ») à
celle d'un APK déjà installé :

```bash
apksigner verify --print-certs gymflemme-2.27.0.apk | grep SHA-256
```

ou, sur le poste qui garde la clé : `keytool -list -v -keystore forme-release.jks -alias forme`. Si la clé est perdue,
les téléphones devront désinstaller l'app (et perdre leurs données, sauf sauvegarde JSON) : garde le `.jks` et ses
mots de passe dans un gestionnaire de mots de passe, avec une copie hors ligne.

## Publier une version

1. Dans une PR, monter la version dans `app/build.gradle.kts` : `versionCode` + 1, `versionName` (`2.28.0` → `2.29.0`),
   et ajouter sa ligne dans `docs/CHANGELOG.md` (`| **V2.29** ✅ | … |` : elle devient les notes de la release et
   de l'écran de mise à jour).
2. Merger la PR quand la CI est verte.
3. Taguer le commit de `main` et pousser le tag :

   ```bash
   git checkout main && git pull
   git tag v2.29.0
   git push origin v2.29.0
   ```

   (ou github.com → *Releases* → *Draft a new release* → *Choose a tag* → `v2.29.0` sur `main` → *Create new tag*,
   puis **annuler** le brouillon : seul le tag compte, la release est créée dans l'autre dépôt.)
4. Onglet **Actions** du dépôt de code → workflow **Publish** : vert en 10 minutes environ. La release apparaît dans
   `gymflemme-releases`, et les téléphones la proposent au prochain lancement (au plus 24 h) ou tout de suite par
   « Rechercher une mise à jour ».

## Si le workflow échoue

Le journal du run indique l'étape en échec ; rien n'est publié tant que la dernière étape n'a pas réussi.

| Message | Cause | Que faire |
|---|---|---|
| `Tag … does not match versionName …` | le tag n'est pas la version de `build.gradle.kts` | supprimer le tag (`git push --delete origin v2.29.0` puis `git tag -d v2.29.0`), corriger, retaguer |
| `Missing secrets: …` | un secret manque ou est vide | le créer (tableau plus haut), puis *Re-run all jobs* |
| tests ou compilation en échec | le commit tagué ne passe pas | corriger dans une PR, merger, puis supprimer et recréer le tag sur le nouveau commit |
| `Release v… already exists` | version déjà publiée | monter la version : une release publiée ne se remplace pas (les téléphones l'ont peut-être déjà) |
| `HTTP 401` / `403` à la dernière étape | jeton expiré ou sans droit `Contents: write` sur `gymflemme-releases` | régénérer le jeton, mettre à jour `GYMFLEMME_RELEASES_TOKEN`, *Re-run all jobs* |

**Publier à la main** (si GitHub Actions est indisponible) :

1. Sur le poste qui a la clé : `gradlew assembleGithubRelease` (avec `keystore.properties`), APK dans
   `app\build\outputs\apk\github\release\`.
2. Le renommer `gymflemme-X.Y.Z.apk`, calculer son empreinte : `certutil -hashfile gymflemme-X.Y.Z.apk SHA256`
   (Windows) ou `sha256sum gymflemme-X.Y.Z.apk` (Linux), l'écrire dans `gymflemme-X.Y.Z.apk.sha256`.
3. Écrire `update.json` :

   ```json
   {
     "versionCode": 60,
     "versionName": "2.28.0",
     "apkUrl": "https://github.com/ollivv-hash/gymflemme-releases/releases/download/v2.28.0/gymflemme-2.28.0.apk",
     "sha256": "<empreinte en minuscules>",
     "minSdk": 28,
     "notes": "Nouveautés de la version, en français"
   }
   ```

4. github.com → `gymflemme-releases` → *Releases* → *Draft a new release* → tag `v2.28.0` → titre « GymFlemme
   2.28.0 » → déposer les trois fichiers → *Publish release*.

Une release ratée (fichier corrompu, mauvaise empreinte) ne casse rien : l'app vérifie le SHA-256 avant d'installer et
supprime un fichier qui ne correspond pas. Supprimer la release fautive et republier avec une version supérieure.

## Variantes

| Variante | Mise à jour | Permission `REQUEST_INSTALL_PACKAGES` | Usage |
|---|---|---|---|
| `github` | depuis `gymflemme-releases` | oui (manifeste `src/github`) | la version publiée |
| `play` | par le store | **non** : la tâche `checkPlayReleaseNoInstallPermission` fait échouer le build si elle apparaît | compilée et testée, pas encore publiée |

Les deux ont le même identifiant (`fr.olivier.forme`) : l'une remplace l'autre sur un téléphone, avec ses données.
