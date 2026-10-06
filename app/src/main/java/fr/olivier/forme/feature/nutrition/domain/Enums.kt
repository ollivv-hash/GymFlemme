package fr.olivier.forme.feature.nutrition.domain

/** Stored as TEXT in Room: never rename a constant without a migration. */
enum class MealType(val label: String) {
    BREAKFAST("Petit-déjeuner"),
    LUNCH("Déjeuner"),
    SNACK("Collation"),
    DINNER("Dîner"),
    /** Additional snacks, listed with their time. */
    EXTRA("Collations supplémentaires"),
}

/**
 * Nutrition goal: adjusts the daily calorie target and the share of it given to fat (sports nutrition: 20–35 %,
 * lower in a cut to keep carbs for training). Stored as TEXT in Room.
 */
enum class NutritionGoal(val label: String, val adjustment: Double, val fatShare: Double, val hint: String) {
    BULK("Prise de masse", 0.10, 0.30, "léger surplus (+10 %) pour construire du muscle en limitant le gras"),
    MAINTAIN("Maintien", 0.0, 0.30, "apport égal à la dépense"),
    CUT("Sèche", -0.20, 0.25, "déficit de 20 % ; garde des protéines hautes pour préserver le muscle"),
}

enum class FoodSource(val label: String) {
    CIQUAL("Ciqual"),
    OFF("Open Food Facts"),
    CUSTOM("Perso"),
}
