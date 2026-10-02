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

enum class FoodSource(val label: String) {
    CIQUAL("Ciqual"),
    OFF("Open Food Facts"),
    CUSTOM("Perso"),
}
