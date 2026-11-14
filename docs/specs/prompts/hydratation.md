# Prompt Claude Code — Suivi de l'hydratation

## Objectif

Ajouter à GymFlemme un suivi quotidien de l'eau bue, avec objectif calculé, saisie rapide (y compris depuis la notification), rappels intelligents et lien avec la nutrition, les séances et les badges.

Les textes d'humour de cette fonction sont dans **docs/specs/textes-hydratation.md**, à intégrer selon les règles du catalogue existant : `res/values/humor.xml` (variantes `humor_*`, version neutre `neutral_*`, version féminine `*_f` pour toute phrase qui s'accorde, comme « bien hydraté / hydratée »), tirage via `QuipRepository`. Badges et défi : `badges.xml`, `challenges.xml` et `feature/badges/domain/BadgeConfig.kt`.

Règles communes (voir aussi `CLAUDE.md`) :
- **Aucune perte de données** : toute évolution du schéma Room passe par une migration explicite, testée.
- Textes hors humour dans `res/values/strings.xml`.
- Les nouvelles données sont incluses dans la sauvegarde JSON (nouvelle version de `schemaVersion`, anciens fichiers toujours importables).
- Les deux variantes `github` et `play` compilent et passent les tests.

---

## 1. Données (Room)

**Drink** (type de boisson)
- `id`, `name`, `icon` (nom d'icône Material Symbols), `hydrationCoefficient` (0.0 à 1.0), `linkedFoodId` (nullable : produit Ciqual ou Open Food Facts pour les calories), `isCaloric` (Boolean), `archived`, `sortOrder`.

**Container** (contenant)
- `id`, `name`, `volumeMl`, `defaultDrinkId`, `isDefault` (un seul contenant par défaut), `archived`, `sortOrder`.

**WaterIntake** (prise)
- `id`, `timestamp` (Instant), `drinkId`, `volumeMl`, `coefficientApplied` (copie du coefficient au moment de la saisie, jamais recalculé), `linkedMealEntryId` (nullable), `source` (`HOME_TILE`, `WATER_SCREEN`, `NOTIFICATION`, `HEALTH_CONNECT_IMPORT` réservé pour plus tard).

**Préférences** (DataStore) : objectif personnalisé activé + valeur, bonus séance activé + valeur, rappels (activés, heure de début, heure de fin, intervalle, pas après une prise récente, pas pendant une séance), écriture Health Connect activée.

**Données initiales (seed)** au premier lancement de cette version :

| Boisson | Icône | Coefficient | Calorique |
|---|---|---|---|
| Eau | `water_full` | 1.0 | Non |
| Eau gazeuse | `bubble_chart` | 1.0 | Non |
| Café | `coffee` | 1.0 | Non |
| Thé / tisane | `emoji_food_beverage` | 1.0 | Non |
| Jus de fruits | `local_bar` | 1.0 | Oui |
| Lait | `water_drop` | 1.0 | Oui |
| Soda | `local_drink` | 1.0 | Oui |
| Soupe / bouillon | `soup_kitchen` | 1.0 | Oui |
| Bière | `sports_bar` | 0.0 | Oui |
| Vin / alcool | `wine_bar` | 0.0 | Oui |

Ajoute ces icônes comme les autres (Material Symbols Outlined, graisse 400, converties telles quelles dans `third_party/material-symbols`, pas de `material-icons-extended`). Vérifie que chaque nom existe ; remplace-le par l'icône la plus proche s'il n'existe pas, et donne-moi la liste des remplacements.

| Contenant | Volume | Boisson par défaut | Par défaut |
|---|---|---|---|
| Verre | 250 ml | Eau | Non |
| Tasse | 200 ml | Café | Non |
| Bouteille | 500 ml | Eau | Non |
| Gourde | 750 ml | Eau | Oui |

---

## 2. Règles de calcul

- **Objectif de base** = poids du profil (dernière pesée) × 35 ml, arrondi aux 100 ml les plus proches. Sans poids : 2 000 ml, avec le message « Renseigne ton poids pour un objectif adapté ».
- **Objectif personnalisé** : remplace l'objectif de base. L'écran affiche toujours les deux (« Calculé : 2,9 L · Utilisé : 2,5 L »).
- **Bonus jour de séance** : +500 ml par défaut (réglable de 0 à 1 500 ml, par pas de 250), ajouté dès qu'une séance est **démarrée** dans la journée, une seule fois par jour. Il s'ajoute à l'objectif de base comme à l'objectif personnalisé, sauf s'il est désactivé.
- **Hydratation comptée** pour une prise = `volumeMl × coefficientApplied`.
- **Jour** : de 00:00 à 23:59, heure locale du téléphone.
- Mention dans Réglages › Hydratation : « Valeurs indicatives, à adapter selon ta situation, ton activité et la chaleur. »

---

## 3. Saisie

### Tuile « Eau » de l'accueil Bento

- Tuile demi-largeur, dans `feature/sport/ui/home/bento/` comme les autres tuiles, placée près de la tuile « Créatine / Compléments » (la grille se réorganise : propose-moi la disposition).
- Contenu : libellé « Eau », volume compté / objectif (« 1,8 / 3,4 L »), jauge en couleur d'accent, mention discrète « +500 ml séance » quand le bonus s'applique.
- **Bouton « + »** (zone tactile ≥ 44 dp) : ajoute le contenant par défaut avec sa boisson par défaut. **Appui long** : ouvre la feuille de choix (boisson + contenant + quantité libre).
- Appui ailleurs sur la tuile : ouvre l'écran Eau.

### Écran Eau

- Progression du jour en grand (anneau ou jauge, cohérent avec le style Bento).
- Rangée de boissons (icône + nom), puis rangée de contenants, puis « Autre quantité » (saisie libre, de 10 à 2 000 ml, pas de 10 ml).
- Historique du jour : heure, icône, boisson, volume ; appui pour modifier (boisson, volume, heure), glissement ou menu pour supprimer.
- Graphique des 7 derniers jours (barres, ligne pointillée de l'objectif), au même style que les autres graphiques de l'app.
- Accès : depuis la tuile de l'accueil et depuis l'onglet Nutrition (entrée « Eau » en tête).

### Après chaque ajout

- Snackbar « +250 ml · Annuler », 5 secondes. Annuler supprime la prise **et** l'entrée de repas liée.

### Boissons caloriques

- Si la boisson est calorique et n'a pas de `linkedFoodId` : au premier ajout, proposer de choisir le produit (recherche Ciqual / Open Food Facts ou scan de code-barres), avec l'option « Ne pas compter les calories ».
- Une fois le produit lié : chaque prise crée automatiquement une entrée de repas (catégorie « Boissons » ou repas en cours selon l'heure, à me proposer) avec la quantité correspondante, et enregistre `linkedMealEntryId`.
- **Lien bidirectionnel** : supprimer la prise supprime l'entrée de repas, et inversement ; modifier le volume de l'une met à jour l'autre.
- La boisson liée peut être changée ou déliée dans Réglages › Hydratation › Mes boissons.

---

## 4. Rappels

- Implémentation : **WorkManager** (tâche périodique ou chaîne de tâches ponctuelles). **Pas** de permission `SCHEDULE_EXACT_ALARM` ni `USE_EXACT_ALARM`.
- **Canal de notification dédié** « Rappels d'hydratation », distinct des canaux créatine et timer, priorité par défaut.
- Paramètres : activés (oui par défaut si l'utilisateur a accordé les notifications), plage horaire (08:00 – 21:00 par défaut), intervalle (60, 90 ou 120 min ; 90 par défaut).
- **Un rappel n'est pas envoyé si** :
  - une prise a été enregistrée dans les 30 dernières minutes (option « Pas juste après avoir bu », activée par défaut) ;
  - l'objectif du jour est atteint ;
  - une séance est en cours (option « Pas pendant une séance », activée par défaut) ;
  - on est hors de la plage horaire.
- **Actions de la notification** : « +250 ml » et « +500 ml » (eau). L'enregistrement se fait sans ouvrir l'app (BroadcastReceiver ou équivalent), la notification se met à jour (« Noté ! 1,8 / 3,4 L ») puis se ferme, et la tuile de l'accueil reflète la prise à la prochaine ouverture.
- Les quantités des deux actions sont réglables (deux contenants au choix).
- Les rappels survivent au redémarrage du téléphone et à la mise à jour de l'app.
- Pas de rappel avant que l'utilisateur ait vu l'écran de configuration de l'hydratation au moins une fois (voir section 7).

---

## 5. Intégrations

### Séances

- Le démarrage d'une séance déclenche le bonus du jour et suspend les rappels jusqu'à la fin de la séance.
- Une séance annulée sans aucune série validée retire le bonus, sauf si une autre séance a été démarrée le même jour.

### Badges et défis (selon le fichier badges existant)

- Badge à paliers **Chameau repenti** : jours avec objectif atteint (non consécutifs) — 7 / 30 / 100 / 250, points 10 / 25 / 50 / 100.
- Badge à paliers **Source intarissable** : jours consécutifs avec objectif atteint — 3 / 7 / 21 / 60.
- Badge secret **Oasis** : au moins 3 L comptés un jour sans séance.
- Défi mensuel **Hydraté** : objectif atteint 20 jours dans le mois. Éligible seulement si l'utilisateur a enregistré de l'eau au moins 5 jours le mois précédent.
- Calcul rétroactif sur l'historique (vide au départ) et idempotent, comme pour les autres badges.

### Health Connect (optionnel)

- Interrupteur « Enregistrer l'hydratation dans Health Connect », désactivé par défaut.
- Si activé : demande de la permission d'écriture `HydrationRecord`, puis chaque prise est écrite (volume compté, pas le volume brut), modifiée ou supprimée en miroir.
- Lecture depuis Health Connect : **hors périmètre** de cette version.
- Ajoute la justification de ce type de donnée dans la documentation de publication (`docs/`), pour la déclaration Play Console.

### Statistiques et bilan

- Si l'écran Statistiques existe : ajouter la courbe d'hydratation (jour, semaine, mois). Sinon, prévoir l'accès aux données pour ce futur écran, sans le créer.
- Si un bilan hebdomadaire existe : y ajouter la moyenne quotidienne et le nombre de jours avec objectif atteint.

---

## 6. Réglages › Hydratation

- Objectif : valeur calculée, interrupteur « Objectif personnalisé » + champ.
- Bonus jour de séance : interrupteur + valeur.
- Mes contenants : liste, ajout, modification, archivage, choix du contenant par défaut, ordre.
- Mes boissons : liste, ajout, modification (nom, icône parmi une sélection, coefficient de 0 à 100 % par pas de 10, calorique, produit lié), archivage, ordre. Une boisson ou un contenant archivé disparaît des choix mais reste dans l'historique.
- Rappels : interrupteur, plage horaire, intervalle, deux options intelligentes, quantités des actions de notification.
- Health Connect : interrupteur.
- Mention « valeurs indicatives ».

---

## 7. Première utilisation

- À la première ouverture de l'écran Eau (ou à l'apparition de la tuile après mise à jour), une carte d'introduction : objectif calculé, contenant par défaut, activation des rappels (avec demande de la permission de notification si elle n'est pas accordée). Bouton « C'est parti » et lien « Plus tard ».
- Pour un nouvel utilisateur, cette étape peut s'intégrer à la fin de l'assistant de profil : propose-moi l'option la plus simple.

---

## 8. Qualité

- **Tests unitaires** : objectif (base, arrondi, personnalisé, bonus, sans poids), coefficient figé dans la prise, conditions d'envoi des rappels (chaque règle et leurs combinaisons, changement de jour), bonus séance (démarrage, annulation, deux séances le même jour), lien boisson ↔ repas (création, modification, suppression dans les deux sens, annulation), badges et défi.
- **Tests de migration** Room.
- **Tests UI** : tuile (ajout rapide, appui long), écran Eau (ajout, modification, suppression), snackbar d'annulation, réglages.
- **Test de l'action de notification** : la prise est bien enregistrée sans ouvrir l'app.
- Accessibilité : chaque boisson et contenant a une description pour TalkBack ; la tuile est annoncée « Eau, 1,8 litre sur 3,4 ».

## Méthode attendue

1. Lis le code existant (accueil Bento, nutrition, séances, badges, notifications), puis propose-moi : le schéma Room, la disposition de la tuile dans la grille, le découpage en étapes. Attends ma validation.
2. Implémente ensuite par étapes testables : données et calculs → écran Eau et tuile → lien nutrition → rappels et actions de notification → badges → Health Connect → réglages et première utilisation.
3. Signale toute ambiguïté au lieu de supposer.
