# Prompt Claude Code — Mise à jour globale de GymFlemme

## Contexte

Cette mise à jour regroupe six chantiers, à livrer **par lots successifs**, chacun testable et commitable séparément, dans l'ordre ci-dessous. Ne passe au lot suivant qu'après ma validation du précédent.

| Lot | Contenu |
|---|---|
| 1 | Corrections rapides : barre de navigation à 5 entrées, icône Réglages, journal Bluetooth |
| 2 | Thème de couleur personnalisable |
| 3 | Profil utilisateur au premier lancement |
| 4 | Onglet « Mes plats » |
| 5 | Partage de modèles de séance (fichier + QR code) |
| 6 | Mise à jour automatique via GitHub + publication automatisée |

Règles communes à tous les lots :

- **Aucune perte de données** pour l'utilisateur existant (moi). Toute évolution du schéma Room passe par une migration explicite, testée.
- Tous les nouveaux textes vont dans `strings.xml`. Les textes d'humour suivent le catalogue existant ; les écrans de profil, d'autorisations, d'erreur et de mise à jour restent **neutres**.
- La sauvegarde JSON et la synchro Google Drive incluent les nouvelles données (profil, préférences de thème, plats épinglés…).
- Le dépôt de code est **public** : aucun secret, aucune clé, aucun jeton dans le code ou l'historique Git. Vérifie que `keystore.properties`, les fichiers `.jks` et tout fichier de secrets sont dans `.gitignore`, et signale-moi s'il en existe déjà dans l'historique.

---

## Lot 1 — Corrections rapides

### Barre de navigation à 5 entrées

- Entrées, dans cet ordre : **Accueil, Séances, Nutrition, Badges, Réglages**.
- Le bouton Réglages de l'en-tête de l'accueil est supprimé.
- Icônes : **Material Symbols** (licence Apache 2.0), même famille et même graisse pour les 5 : `home`, `fitness_center`, `restaurant`, `military_tech`, `settings`. Ne dessine aucune icône à la main.
- Le style reste celui de la refonte Bento (pilule flottante, onglet actif sur fond accent). Chaque entrée garde une zone tactile d'au moins 44 × 44 dp et une `contentDescription`.

### Journal Bluetooth (menu Montre cardio)

- Retire l'entrée « Journal Bluetooth » du menu Montre cardio des Réglages.
- **Ne supprime pas le code** : rends-le accessible via un **mode développeur**, activé par 7 appuis sur le numéro de version (Réglages › À propos), avec un message « Mode développeur activé ». Le mode se désactive depuis le même écran.

---

## Lot 2 — Thème de couleur personnalisable

Dans Réglages › Apparence :

- **Mode** : Clair / Sombre / Suivre le système (par défaut : Suivre le système).
- **Couleur d'accent**, 6 choix prédéfinis, affichés en pastilles :

| Nom | Accent | Texte sur accent |
|---|---|---|
| Vert (défaut) | `#8BD48F` | `#0F2412` |
| Bleu | `#8DBDF0` | `#0E1F33` |
| Orange | `#F4B678` | `#2E1A07` |
| Citron vert | `#B7E36A` | `#1A2608` |
| Lavande | `#C3A6F2` | `#1F1236` |
| Corail | `#F29C8F` | `#330F0A` |

- **Couleurs dynamiques** (Material You, Android 12 et plus) : option désactivée par défaut ; si elle est activée, l'accent vient du fond d'écran et les pastilles sont grisées.
- L'accent sert partout où la refonte Bento l'utilise : tuile séance, onglet actif, objectif atteint, barres de progression. Aucune couleur d'accent en dur dans les écrans : tout passe par le thème.
- Changement **immédiat**, sans redémarrage.
- **Test** : un test calcule le contraste de chaque couple (accent / texte sur accent, texte principal / fond, en clair et en sombre) et échoue sous 4,5:1. Si une valeur du tableau échoue, propose-moi une correction plutôt que de l'ajuster en silence.

---

## Lot 3 — Profil utilisateur au premier lancement

### Assistant de configuration

Affiché au **premier lancement uniquement**, en 4 écrans avec indicateur de progression, bouton « Passer » sur chaque écran et retour arrière possible :

1. **Toi** : prénom.
2. **Ton corps** : année de naissance (pas l'âge, qui change), sexe (Homme / Femme / Ne pas préciser), taille (cm), poids (kg).
3. **Ton objectif** : objectif (Perdre du poids / Maintenir / Prendre du muscle), niveau d'activité (Sédentaire / Léger / Modéré / Actif / Très actif, chacun avec une phrase d'explication).
4. **Tes compléments** : grammes de protéines par dose de whey (optionnel), dose de créatine (3 g par défaut).

Écran final : récapitulatif avec la cible de calories et la cible de protéines calculées, modifiables avant de valider.

- **Utilisateur existant** : si un profil existe déjà (poids saisi, cibles en place), l'assistant ne s'affiche pas. Les champs nouveaux restent vides et se remplissent depuis Réglages › Profil.
- Tous les champs sont modifiables ensuite dans **Réglages › Profil**.
- Validation des saisies : bornes raisonnables (taille 100–250 cm, poids 30–300 kg, année de naissance donnant 13 à 100 ans), message clair en cas d'erreur.
- **Âge minimum** : en dessous de 16 ans, l'app n'affiche pas de cible de calories ni de suggestion de compléments, et affiche « Demande conseil à un adulte ou à un professionnel de santé pour tes objectifs nutritionnels. »

### Cible de calories : calculée, mais personnalisable

- **Commence par me dire comment l'app calcule la cible aujourd'hui** (formule, paramètres). Si un calcul existe déjà, garde-le et adapte-le aux nouveaux champs ; ne change pas de formule sans mon accord.
- À défaut de calcul existant, propose : métabolisme de base de **Mifflin-St Jeor** × facteur d'activité (1,2 / 1,375 / 1,55 / 1,725 / 1,9), puis ajustement selon l'objectif (perte : −15 %, maintien : 0, prise : +10 %). « Ne pas préciser » pour le sexe : moyenne des deux formules.
- Un interrupteur **« Cible personnalisée »** permet de remplacer la valeur calculée par une valeur saisie. L'écran affiche toujours les deux : « Calculée : 2 137 kcal · Utilisée : 2 000 kcal ». Désactiver l'interrupteur revient au calcul.
- La cible calculée se met à jour quand le poids, l'objectif ou l'activité changent ; une cible personnalisée ne change jamais seule.
- Si une cible personnalisée est inférieure au métabolisme de base calculé, afficher un avertissement neutre : « Cette cible est inférieure à ton métabolisme de base. Demande conseil à un professionnel de santé avant de la conserver. » (sans bloquer la saisie).
- Cible de protéines : 1,6 g/kg (déjà en place), même principe calculée / personnalisée.

---

## Lot 4 — Onglet « Mes plats »

Problème actuel : mes plats personnalisés sont noyés parmi tous les aliments.

- **Commence par me décrire comment les plats personnalisés sont stockés aujourd'hui** (entité, champs, lien avec les aliments) avant de modifier quoi que ce soit.
- Dans l'écran d'ajout d'un repas, des onglets en tête : **Mes plats** (ouvert par défaut) · **Aliments** · **Scanner**.
- **Mes plats** :
  - Recherche limitée à mes plats.
  - Plats **épinglés** en tête (appui long ou icône pour épingler / désépingler).
  - Puis tri au choix : **Les plus utilisés** (par défaut) · **Récents** · **A → Z**.
  - Chaque plat : nom, calories et protéines par portion, ajout en un appui avec la portion habituelle, modification de la portion en un second appui.
  - Bouton « Créer un plat » en bas de liste.
  - État vide : « Aucun plat enregistré. Crée ton premier plat à partir des aliments que tu manges souvent. »
- **Aliments** : la recherche générale existante (Open Food Facts, Ciqual). Mes plats n'y apparaissent plus, sauf si je cherche un nom qui correspond à l'un d'eux : il s'affiche alors dans une section « Mes plats » en haut des résultats.
- Données ajoutées par plat : `pinned`, `useCount`, `lastUsedAt`, mis à jour à chaque ajout dans un repas. Migration : initialiser `useCount` et `lastUsedAt` à partir de l'historique des repas existants.

---

## Lot 5 — Partage de modèles de séance

Deux personnes avec chacune leur téléphone peuvent s'échanger un modèle de séance, sans serveur.

### Ce qui est partagé

La **structure** du modèle uniquement : nom, exercices dans l'ordre, séries, répétitions min/max, durée cible, temps de repos, exercices optionnels. Pour chaque exercice : `libraryId` (Open Exercise Illustrations) s'il existe, nom, type, groupe musculaire, incrément de charge.

**Jamais** : charges, historique, poids, profil, données de santé. Le prénom de l'auteur seulement si l'utilisateur coche « Afficher mon prénom » au moment du partage.

### Format

```json
{
  "format": "gymflemme-template",
  "formatVersion": 1,
  "appVersion": "1.4.0",
  "author": "Olivier",
  "template": {
    "name": "B · Dos",
    "exercises": [
      {
        "position": 1,
        "libraryId": "lat-pulldown",
        "name": "Pull down",
        "type": "WEIGHTED",
        "muscleGroup": "DOS",
        "loadIncrementKg": 2.5,
        "targetSets": 3,
        "targetRepsMin": 10,
        "targetRepsMax": 12,
        "targetDurationSec": null,
        "restSec": 90,
        "optional": false
      }
    ]
  }
}
```

Le schéma est un exemple : adapte les noms de champs au modèle de données existant et documente le format final dans le dépôt (`docs/format-partage.md`). L'import refuse proprement un `formatVersion` plus récent que celui que l'app comprend (« Ce modèle vient d'une version plus récente de GymFlemme. Mets l'app à jour pour l'importer. »).

### Envoyer

Sur un modèle (Réglages › Modèles de séance, menu du modèle) : **Partager**, avec deux choix :

- **Envoyer un fichier** : fichier `<nom-du-modele>.gymflemme` partagé via la feuille de partage Android (WhatsApp, mail, Bluetooth…), via un `FileProvider`.
- **Afficher un QR code** : contenu JSON compressé (deflate) puis encodé, préfixé par `GYMFLEMME1:`. Si le contenu dépasse la capacité d'un QR code lisible (niveau de correction M), l'option QR est grisée avec « Modèle trop long pour un QR code, utilise l'envoi par fichier ».

### Recevoir

- **Par fichier** : l'app se propose à l'ouverture d'un fichier `.gymflemme` (filtres d'intention adaptés ; teste avec WhatsApp, Gmail et le gestionnaire de fichiers). Ajoute aussi un bouton « Importer un modèle » dans Réglages › Modèles de séance (sélecteur de fichier système), car l'association d'extension n'est pas fiable sur toutes les apps.
- **Par QR code** : « Scanner un modèle » dans le même écran ; réutilise le scanner ML Kit déjà présent pour les codes-barres.

### Écran de prévisualisation avant import

- Nom du modèle, auteur s'il est fourni, liste des exercices.
- **Correspondance des exercices**, pour chacun, dans cet ordre :
  1. même `libraryId` dans mon catalogue → reconnu ;
  2. sinon même nom exact (sans tenir compte de la casse ni des accents) → reconnu ;
  3. sinon → **à créer** dans mon catalogue, avec les infos du fichier.
- Récapitulatif : « 4 exercices reconnus, 1 à créer : Lower back ». Pour chaque exercice reconnu, je peux choisir un autre exercice de mon catalogue.
- Bouton **Importer** ; aucune modification de ma base avant ce bouton.

### Après import

- Le modèle importé est une **copie indépendante** : je le modifie librement, et l'auteur ne peut pas le modifier à distance.
- **Conflit de nom** : s'il existe déjà un modèle du même nom, l'import devient « {nom} ({auteur}) » ou « {nom} (importé) », renommable.
- Le modèle n'est **pas ajouté à la rotation** automatiquement ; l'écran de fin propose « Ajouter à ma rotation ».
- À la première séance avec ce modèle, aucune charge n'est pré-remplie pour les exercices sans historique.

---

## Lot 6 — Mise à jour automatique via GitHub

### Deux variantes de l'app

Crée deux *product flavors*, depuis le même code :

- **`github`** : contient la mise à jour automatique. C'est la version que j'utilise.
- **`play`** : sans aucun code ni permission de mise à jour (le Play Store interdit l'auto-mise à jour et restreint la permission `REQUEST_INSTALL_PACKAGES`). Elle doit compiler et passer les tests, même si je ne la publie pas encore.

Le code de mise à jour et la permission `REQUEST_INSTALL_PACKAGES` ne doivent exister **que** dans la variante `github` (source set et manifeste dédiés). Ajoute un test ou une vérification de build qui échoue si la permission apparaît dans la variante `play`.

### Dépôts

- Dépôt de code : public (existant).
- Dépôt de versions : **nouveau dépôt public**, `{owner}/gymflemme-releases`, qui ne contient que les publications (Releases GitHub), pas de code. Donne-moi les étapes pour le créer.

### Manifeste de version

Chaque Release du dépôt de versions contient trois fichiers :

- `gymflemme-{versionName}.apk` (variante `github`, signée) ;
- `gymflemme-{versionName}.apk.sha256` ;
- `update.json` :

```json
{
  "versionCode": 14,
  "versionName": "1.4.0",
  "apkUrl": "https://github.com/{owner}/gymflemme-releases/releases/download/v1.4.0/gymflemme-1.4.0.apk",
  "sha256": "…",
  "minSdk": 28,
  "notes": "Nouveautés de la version, en français"
}
```

### Comportement dans l'app

- **Vérification** au lancement, au plus une fois toutes les 24 h, via l'API GitHub (`/repos/{owner}/gymflemme-releases/releases/latest`, sans authentification), puis lecture de `update.json`. Plus un bouton **« Rechercher une mise à jour »** dans Réglages › À propos.
- Réglage **« Télécharger uniquement en Wi-Fi »**, activé par défaut.
- Si `versionCode` est supérieur à celui installé : carte discrète sur l'accueil et écran avec le numéro de version et les notes. Rien ne s'installe sans mon accord. Jamais de rétrogradation.
- **Téléchargement** dans le cache de l'app, avec progression ; **vérification du SHA-256** avant installation ; en cas d'écart, suppression du fichier et message d'erreur neutre.
- **Installation** via l'API `PackageInstaller` (session), avec confirmation système.
- **Première fois** : si l'autorisation « Installer des applis inconnues » n'est pas accordée pour GymFlemme, écran d'explication puis ouverture directe du bon écran des paramètres Android.
- **Erreurs** (pas de réseau, limite de l'API GitHub, fichier corrompu) : messages neutres, jamais de blocage de l'app. La vérification se refait au lancement suivant.
- La mise à jour ne peut aboutir que si l'APK est signé avec **la même clé** que l'app installée : vérifie que la configuration de signature actuelle est bien celle utilisée par le workflow.

### Publication automatisée (GitHub Actions)

Workflow dans le **dépôt de code**, déclenché par un tag `v*` (ex. `v1.4.0`) :

1. Lance les tests.
2. Compile `assembleGithubRelease`, signé avec la clé stockée dans les **secrets du dépôt** (keystore encodé en base64 + mots de passe + alias). Rien de tout ça ne doit apparaître dans les journaux.
3. Calcule le SHA-256, génère `update.json` (versionCode et versionName lus depuis la configuration Gradle ; échec si le tag ne correspond pas au versionName).
4. Crée la Release dans `gymflemme-releases` avec les trois fichiers, via un **jeton à accès restreint** (fine-grained token, droit `contents: write` sur le seul dépôt de versions), stocké en secret.

Fournis-moi dans `docs/publication.md` :

- la liste des secrets à créer et la commande pour encoder la clé en base64 (sous Windows PowerShell et sous Linux) ;
- la création du jeton fine-grained ;
- la procédure pour publier une version (incrémenter, taguer, pousser) ;
- la procédure de secours si le workflow échoue.

**Ne crée aucun secret et ne pousse aucun tag toi-même** : prépare tout, je fais les manipulations sur GitHub.

---

## Qualité

- Tests unitaires : calcul des cibles (calculée / personnalisée / mise à jour sur changement de poids), contraste des thèmes, tri et épinglage des plats, export / import d'un modèle (aller-retour sans perte, correspondance des exercices, conflit de nom, version de format trop récente), comparaison de versions et vérification SHA-256.
- Tests de migration Room pour chaque évolution de schéma.
- Tests UI : assistant de profil (y compris « Passer » et utilisateur existant), barre à 5 entrées, onglet Mes plats, prévisualisation d'import.
- Les deux variantes `github` et `play` compilent et passent les tests.

## Méthode attendue

1. Lis le code existant, puis donne-moi pour **le lot 1 uniquement** : les fichiers touchés et ce qui change. Attends ma validation avant de coder.
2. Pour chaque lot suivant, même démarche : plan court, validation, implémentation, tests, commit.
3. Pour les lots 3 et 4, commence par les deux questions posées dans le texte (calcul actuel des calories, stockage actuel des plats).
4. Signale toute ambiguïté au lieu de supposer.
