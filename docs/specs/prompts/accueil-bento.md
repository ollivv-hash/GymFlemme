# Prompt Claude Code — Refonte de la page d'accueil (style « Bento »)

## Objectif

Refaire l'écran d'accueil de GymFlemme dans un style « bento » : une grille de tuiles arrondies de tailles différentes, une grande tuile pour la prochaine séance, une barre de navigation flottante. Plus moderne et plus fin que l'accueil actuel.

**Seul l'écran d'accueil change.** Les données, ViewModels et règles métier existants restent tels quels ; seuls la présentation et la navigation basse évoluent. Ne modifie aucun autre écran sans me le demander.

## Référence visuelle

Maquette validée : canevas « GymFlemme — Accueil, pistes de design », artboard **C · Bento** (390 × 844). Les valeurs ci-dessous en sont tirées ; en cas de doute, elles font foi.

## Design tokens (à créer dans le thème, pas en dur dans l'écran)

**Typographie** : Space Grotesk (Google Fonts, licence OFL), graisses 400 / 500 / 600 / 700. Intègre-la en police embarquée (`res/font`), pas en téléchargement à la volée. Utilise-la pour l'accueil ; propose-moi de l'étendre au reste de l'app avant de le faire.

| Style | Taille | Graisse | Interlettrage |
|---|---|---|---|
| Titre de séance | 40 sp | 700 | -1,2 |
| Chiffre de tuile | 30 sp | 600 | -0,8 |
| Salutation | 24 sp | 600 | -0,5 |
| Titre de tuile | 16 sp | 600 | 0 |
| Bouton | 15 sp | 600 | 0 |
| Corps | 13–14 sp | 500 | 0 |
| Libellé | 12 sp | 400–600 | 0 (1 en majuscules) |

**Couleurs — thème clair (maquette)**

| Rôle | Valeur |
|---|---|
| Fond de l'écran | `#EEEEE9` |
| Tuile claire | `#FFFFFF` |
| Tuile sombre, barre de navigation | `#16191B` |
| Texte principal | `#16191B` |
| Texte secondaire | `#5E6468` |
| Texte sur tuile sombre | `#F2F3F0` / secondaire `#A9AFB2` |
| Accent (tuile séance, onglet actif, objectif atteint) | `#8BD48F` |
| Texte sur accent | `#0F2412` |
| Barre calories | `#8DBDF0` |
| Piste de progression (fond) | `#DCDDD7` sur tuile claire, `#2C3134` sur tuile sombre |
| Icônes inactives de la barre | `#C4C9CB` |

**Thème sombre** (l'app est en sombre par défaut) : la maquette est en clair. Propose-moi une déclinaison sombre qui garde la même structure (fond `#0D1012`, tuiles `#1A1F22`, tuile séance en accent, barre de navigation `#22282C`) et montre-moi les deux avant de finaliser. Tous les contrastes texte/fond doivent atteindre 4,5:1 (3:1 pour les chiffres de 24 sp et plus).

**Formes et espacements**

- Rayon des tuiles : 26 dp. Rayon de la barre de navigation : 22 dp. Pastilles et boutons : entièrement arrondis.
- Marges latérales de l'écran : 18 dp. Espace entre tuiles : 10 dp. Padding intérieur des tuiles : 16 dp (20 dp pour la tuile séance).
- Aucune ombre portée, aucun dégradé : uniquement des aplats.
- Zones tactiles : 44 × 44 dp minimum.

## Structure de l'écran (de haut en bas)

1. **En-tête** : à gauche, « Salut {prénom} » (12 sp, secondaire) puis la salutation du catalogue d'humour (24 sp). À droite, bouton carré arrondi 44 dp vers les Réglages.
2. **Grille 2 colonnes** :
   - **Tuile séance** (pleine largeur, fond accent) : libellé « PROCHAINE SÉANCE », nom de la séance en 40 sp (« B · Dos »), pastille « Sem. 2 / 3 » en haut à droite, en bas « 5 exercices · ~40 min » et le bouton de démarrage (pilule sombre, texte du catalogue d'humour, section 3).
   - **Tuile calories** (demi-largeur, fond sombre, hauteur 170 dp) : libellé, chiffre du jour, « sur {cible} kcal », barre de progression.
   - **Tuile protéines** (demi-largeur, fond blanc, hauteur 170 dp) : libellé, grammes du jour, « objectif {cible} g » ou « objectif {cible} g atteint », barre de progression (pleine en accent si atteint).
   - **Tuile défi du mois** (demi-largeur) : libellé, nom du défi, progression en segments (un segment par unité si l'objectif ≤ 15, sinon une barre continue), « {n} / {objectif} ».
   - **Tuile créatine** (demi-largeur) : libellé, série en cours (« 12 jours d'affilée »), case à cocher « {dose} g pris » qui enregistre directement la prise du jour.
   - **Tuile niveau** (pleine largeur, cliquable vers l'écran Badges) : carré sombre avec le numéro de niveau, titre du niveau, barre vers le niveau suivant, « {points} / {seuil} ».
3. **Barre de navigation flottante** : pilule sombre détachée des bords (marges 18 dp, 16 dp en bas), 64 dp de haut, 4 entrées : Accueil, Séances, Nutrition, Badges. L'onglet actif est un carré arrondi en accent ; les autres sont des icônes seules avec `contentDescription`. Les Réglages passent dans l'en-tête de l'accueil.

## Comportements

- **Défilement** : si le contenu dépasse l'écran (petit téléphone, grande taille de police système), la grille défile ; la barre de navigation reste fixe au-dessus.
- **Données absentes** : pas de repas saisi aujourd'hui → les tuiles calories et protéines affichent « — » et un lien « Ajouter un repas » ; pas de défi éligible ce mois → la tuile défi est masquée et la créatine passe en pleine largeur ; module nutrition désactivé → tuiles calories et protéines masquées.
- **Mode sérieux** : les textes humoristiques (salutation, bouton) passent en version neutre, comme partout.
- **Taille de police système** : l'écran reste lisible jusqu'à 130 % (le texte peut passer à la ligne, jamais être tronqué au milieu d'un chiffre).
- **Accessibilité** : chaque tuile annoncée comme un seul élément par TalkBack (« Calories, 2 012 sur 2 137 kilocalories »).
- **Animations** : légères uniquement (barres de progression qui se remplissent à l'ouverture, 300 ms) ; respecter le réglage système « Supprimer les animations ».

## Qualité

- Composables découpés par tuile (`SessionTile`, `CaloriesTile`, …), chacun avec ses `@Preview` en clair et en sombre, avec et sans données.
- Tests UI Compose : affichage de chaque tuile, masquage des tuiles sans données, case créatine qui enregistre la prise, navigation vers Badges et Réglages.
- Captures d'écran de l'accueil en clair et en sombre, à me montrer avant la validation finale.

## Méthode attendue

1. Propose-moi d'abord le découpage en composables, les tokens ajoutés au thème et la déclinaison sombre. Attends ma validation.
2. Implémente ensuite par étapes : thème et police → tuiles → barre de navigation → états vides → tests.
3. Signale toute ambiguïté au lieu de supposer.
