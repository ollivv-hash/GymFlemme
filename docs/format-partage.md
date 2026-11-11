# Format de partage d'un modèle de séance

Depuis la V2.27, deux téléphones peuvent s'échanger un modèle de séance sans serveur : par un **fichier** `.gymflemme`
(feuille de partage Android : WhatsApp, mail, Bluetooth…) ou par un **QR code** affiché sur un téléphone et scanné par
l'autre. Code : `feature/sharing/`.

## Ce qui est partagé

La **structure** du modèle seulement : nom, code (A, B…), exercices dans l'ordre, séries, répétitions min / max, durée
cible, repos, exercices facultatifs, supersets. Pour chaque exercice : `libraryId` (bibliothèque Open Exercise
Illustrations) s'il existe, nom, type, groupe musculaire, « par côté », incrément de charge.

**Jamais** : charges, historique, poids, profil, données de santé, notes de réglage ou de charge, photos, note « pour la
prochaine fois ». Le prénom de l'auteur seulement si « Afficher mon prénom » est coché au moment du partage.

## Fichier `.gymflemme`

JSON en UTF-8, type MIME `application/vnd.gymflemme.template+json`. Les champs absents prennent leur valeur par défaut ;
les champs inconnus sont ignorés (un fichier d'une version plus récente du même `formatVersion` reste lisible).

```json
{
  "format": "gymflemme-template",
  "formatVersion": 1,
  "appVersion": "2.27.0",
  "author": "Olivier",
  "template": {
    "name": "Tirage + tronc",
    "code": "B",
    "exercises": [
      {
        "position": 1,
        "libraryId": "lat-pulldown",
        "name": "Pull down",
        "type": "WEIGHTED",
        "muscleGroup": "DOS",
        "perSide": false,
        "loadIncrementKg": 2.5,
        "targetSets": 3,
        "targetRepsMin": 10,
        "targetRepsMax": 12,
        "restSec": 90,
        "optional": false,
        "supersetGroup": 1
      }
    ]
  }
}
```

| Champ | Type | Obligatoire | Sens |
|---|---|---|---|
| `format` | texte | oui | Toujours `gymflemme-template`. |
| `formatVersion` | entier | oui | Version du format : **1**. Un fichier d'une version supérieure est refusé : « Ce modèle vient d'une version plus récente de GymFlemme. Mets l'app à jour pour l'importer. » |
| `appVersion` | texte | oui | Version de GymFlemme qui a écrit le fichier (information). |
| `author` | texte | non | Prénom de l'auteur, seulement s'il a choisi de l'afficher. |
| `template.name` | texte | oui | Nom du modèle. |
| `template.code` | texte | non | Code court (A, B, BAS…). |
| `template.exercises` | liste | oui (≥ 1) | Exercices, dans l'ordre de `position`. |
| `position` | entier | oui | Ordre, à partir de 1. |
| `libraryId` | texte | non | Identifiant de la bibliothèque d'exercices. |
| `name` | texte | oui | Nom de l'exercice. |
| `type` | texte | oui | `WEIGHTED` (charge + reps), `BODYWEIGHT` (poids du corps), `TIMED` (durée), `CARDIO`. |
| `muscleGroup` | texte | oui | `PECTORAUX`, `EPAULES`, `DOS`, `BRAS`, `ABDOS`, `JAMBES`, `CARDIO`… (noms de `MuscleGroup`). |
| `perSide` | booléen | non (`false`) | Durée par côté (exercices `TIMED`). |
| `loadIncrementKg` | nombre | non (2,5) | Pas de progression de la charge. |
| `targetSets` | entier | oui (1 à 20) | Séries visées. |
| `targetRepsMin`, `targetRepsMax` | entier | non | Fourchette de répétitions. |
| `targetDurationSec` | entier | non | Durée visée (exercices `TIMED`, `CARDIO`). |
| `restSec` | entier | non | Repos après une série ; absent = repos par défaut du téléphone qui importe. |
| `optional` | booléen | non (`false`) | Exercice facultatif. |
| `supersetGroup` | entier | non | Les exercices qui partagent ce numéro forment un superset. |

Un fichier qui n'est pas du JSON, dont `format` diffère, sans exercice, ou avec un type ou un groupe inconnu est refusé
(« Ce fichier n'est pas un modèle GymFlemme lisible. »).

## QR code

Contenu : `GYMFLEMME1:` suivi du JSON **compact** (même schéma), compressé en **deflate brut** (sans en-tête zlib) puis
encodé en **base64** (sans `=` final). QR code de niveau de correction **M**, mode octets. Au-delà de la **version 25**
(environ 1 000 caractères), le code n'est plus lisible sur un écran de téléphone : l'option « Afficher un QR code » est
grisée (« Modèle trop long pour un QR code, utilise l'envoi par fichier. »). Pour repère : un modèle de 2 exercices
tient en version 16, un modèle de 8 exercices en version 19. Le QR code est dessiné avec ZXing (Apache 2.0) et lu par
ML Kit.

## Import

1. **Prévisualisation** : nom (modifiable), auteur s'il est fourni, exercices. Rien n'est écrit avant **Importer**.
2. **Correspondance**, pour chaque exercice, dans cet ordre : même `libraryId` dans le catalogue (exercices non
   archivés) → reconnu ; sinon même nom, sans tenir compte de la casse ni des accents → reconnu ; sinon → à créer avec
   les valeurs du fichier. Un exercice reconnu reste celui du catalogue (son incrément, ses notes) ; « Changer »
   permet d'en choisir un autre, ou de créer celui du fichier. Récapitulatif : « 4 exercices reconnus, 1 à créer :
   Lower back ».
3. **Copie indépendante** : le modèle importé se modifie librement ; l'auteur ne peut rien y changer.
4. **Nom déjà pris** : « {nom} ({auteur}) » ou « {nom} (importé) », numéroté s'il le faut ; renommable avant d'importer.
5. Le modèle **n'entre pas dans la rotation** ; l'écran de fin propose « Ajouter à ma rotation » (en dernière position).
6. À la première séance, un exercice créé n'a pas d'historique : aucune charge n'est pré-remplie.

## Ouvrir un fichier reçu

- **Réglages → Modèles de séance → Importer un modèle** : sélecteur de fichiers du système. Toujours fiable.
- **Ouverture directe** depuis une autre app : GymFlemme se propose pour le type `application/vnd.gymflemme.template+json`
  et, chez les gestionnaires de fichiers qui transmettent le nom, pour un fichier `*.gymflemme` de type générique
  (`application/octet-stream`, `application/json`, `text/plain`). Selon l'app d'origine, le fichier peut arriver sans
  nom ni type : dans ce cas, l'enregistrer puis passer par « Importer un modèle ».
- **Scanner un modèle** : caméra, QR codes uniquement ; un QR code d'autre chose est signalé.

## Faire évoluer le format

Un champ **facultatif** ajouté ne change pas `formatVersion` (les versions plus anciennes l'ignorent). Un changement qui
rendrait un fichier mal compris par une version plus ancienne (champ obligatoire, sens changé) passe `formatVersion` à 2 ;
le préfixe du QR code devient alors `GYMFLEMME2:`.
