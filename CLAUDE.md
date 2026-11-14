# GymFlemme — consignes pour Claude Code

App Android perso (Kotlin, Jetpack Compose) de suivi des séances de musculation et de la nutrition. Ce fichier donne
les règles permanentes du projet ; le fonctionnement détaillé est dans `docs/ARCHITECTURE.md`, la compilation et la
publication dans `docs/DEVELOPPEMENT.md` et `docs/publication.md`.

## Méthode de travail

- **Proposer avant de coder** : plan court (fichiers touchés, schéma, étapes), puis attendre la validation d'Olivier.
- Implémenter **par étapes testables**, commits atomiques, messages explicites.
- **Signaler toute ambiguïté au lieu de supposer.** Ne pas modifier un écran ou un comportement hors du périmètre demandé.
- Avant chaque push, les vérifications complètes :
  `gradlew testGithubDebugUnitTest testPlayDebugUnitTest lintGithubDebug lintPlayDebug assembleGithubRelease assemblePlayRelease`
- **Une branche par chantier**, une PR par version. Ne jamais pousser directement sur `main`.
- **Toute fusion sur `main` d'une PR qui monte la version publie une release** (`.github/workflows/publish.yml`) et la
  propose en mise à jour automatique : ne monter `versionCode` / `versionName` qu'une fois le chantier terminé et validé.
- Tenir à jour `docs/ARCHITECTURE.md` et `docs/CHANGELOG.md` (une ligne par version) à chaque évolution.

## Règles techniques

- Package `fr.olivier.forme` **à ne jamais changer** (noms internes `Forme…` dans le code).
- Interface en **français**, code (identifiants, commentaires) en **anglais**.
- **Hors ligne d'abord** : aucune API IA, aucun service payant, aucune clé d'API dans le code. Seul accès réseau
  autorisé à ce jour : Open Food Facts (produit jamais scanné) et la vérification de mise à jour (variante `github`).
- Architecture MVVM `ui` / `domain` / `data`, organisée par fonctionnalité (`feature/…`), Hilt, `StateFlow`.
- **Room** : toute évolution du schéma passe par une **migration explicite**, testée (`room-testing`), schéma exporté
  dans `app/schemas/`. Aucune perte de données pour un utilisateur existant.
- **Sauvegarde JSON** : toute nouvelle donnée utilisateur y est ajoutée, avec une nouvelle `schemaVersion` ; les
  anciens fichiers restent importables.
- **Deux variantes** (`github`, `play`) qui compilent et passent les tests. Rien de la mise à jour automatique ni la
  permission `REQUEST_INSTALL_PACKAGES` dans `play`.
- Pas de nouvelle dépendance sans justification (ajoutée au tableau de `docs/ARCHITECTURE.md`).
- Icônes : **Material Symbols** Outlined, graisse 400, converties dans `third_party/material-symbols`. Aucune icône
  dessinée à la main, pas de `material-icons-extended`.
- Couleurs et typo de l'accueil : jetons de `ui/theme/Bento.kt` et accent de `ui/theme/Accents.kt`, jamais en dur.
  Contrastes texte ≥ 4,5:1 (`ThemeContrastTest`).
- Zones tactiles ≥ 48 dp, `contentDescription` sur les icônes seules, TalkBack lit chaque tuile d'un bloc.

## Textes et ton

- Aucun texte en dur pour les nouveaux écrans : `res/values/strings.xml`.
- **Humour** : `res/values/humor.xml`. Pour chaque entrée : variantes `humor_*`, version neutre `neutral_*` (mode
  sérieux) et version féminine `*_f` pour toute phrase qui s'accorde (choisie si le profil indique « Femme »).
  Tirage via `QuipRepository` (jamais deux fois de suite la même variante, stable tant que l'écran est affiché).
- Ton : **taquin et complice**, on se moque de la flemme, jamais de la personne. **Jamais d'humour sur le corps, le
  poids, l'assiette ou l'alcool.** Ne pas réécrire le ton des textes fournis ; signaler une phrase trop longue pour
  l'écran au lieu de la changer.
- **Toujours neutres** : poids, repas, mentions santé, À propos, erreurs, confirmations, autorisations, mise à jour.
- Badges, défis, niveaux : effort et régularité uniquement, jamais le poids. Seuils dans
  `feature/badges/domain/BadgeConfig.kt` ; calcul idempotent, rien ne se perd.
- Santé : les suggestions (compléments, charges, calories, eau) sont **indicatives** et le disent ; jamais de
  conseil médical. Moins de 16 ans : pas de cible calories ni de conseil compléments.

## Sécurité

- **Le dépôt est public** : aucun secret, mot de passe, jeton ni keystore dans le code ou l'historique.
  `keystore.properties`, `*.jks`, `*.keystore` restent dans `.gitignore` ; la signature CI passe par les secrets du dépôt.
- Aucune donnée personnelle dans les journaux de la version release.

## Documents de référence

- `docs/ARCHITECTURE.md` : fonctionnement réel de l'app — **fait foi** en cas de doute.
- `docs/specs/` : spécifications et prompts d'origine de chaque chantier (catalogue d'humour, badges, hydratation…).
  Le code et `ARCHITECTURE.md` priment sur une spec plus ancienne.
- `docs/format-partage.md` : format des fichiers `.gymflemme`.
