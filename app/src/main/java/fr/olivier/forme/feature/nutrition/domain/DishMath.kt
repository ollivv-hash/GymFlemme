package fr.olivier.forme.feature.nutrition.domain

/** An ingredient of a dish (V2.21): its values per 100 g as they were when added, and its grams. */
data class DishIngredient(val name: String, val grams: Double, val per100: Per100)

/**
 * A dish's values (V2.21): the sum of its ingredients, brought back to 100 g of the cooked dish when its cooked
 * weight is known (rice and pasta take on water), otherwise of the raw ingredients. A portion is that weight
 * shared by the number of servings.
 */
object DishMath {
    fun rawWeight(ingredients: List<DishIngredient>): Double = ingredients.sumOf { it.grams }

    /** Weight the values per 100 g refer to: the cooked weight when given, else the raw ingredients. */
    fun weight(ingredients: List<DishIngredient>, cookedWeightG: Double?): Double =
        cookedWeightG?.takeIf { it > 0 } ?: rawWeight(ingredients)

    /** Values of the whole dish; kcal, carbs or fat are unknown only when unknown for every ingredient. */
    fun totals(ingredients: List<DishIngredient>): Macros {
        val parts = ingredients.map { NutritionMath.forQuantity(it.per100, it.grams) }
        fun sumKnown(pick: (Macros) -> Double?) = parts.mapNotNull(pick).takeIf { it.isNotEmpty() }?.sum()
        return Macros(parts.sumOf { it.proteinG }, sumKnown { it.kcal }, sumKnown { it.carbsG }, sumKnown { it.fatG })
    }

    /** Null while the dish weighs nothing. */
    fun per100(ingredients: List<DishIngredient>, cookedWeightG: Double?): Per100? {
        val weight = weight(ingredients, cookedWeightG).takeIf { it > 0 } ?: return null
        val t = totals(ingredients)
        val f = 100.0 / weight
        return Per100(t.proteinG * f, t.kcal?.times(f), t.carbsG?.times(f), t.fatG?.times(f))
    }

    /** One serving, in grams of the dish. */
    fun portion(ingredients: List<DishIngredient>, cookedWeightG: Double?, servings: Int): Double =
        weight(ingredients, cookedWeightG) / servings.coerceAtLeast(1)
}
