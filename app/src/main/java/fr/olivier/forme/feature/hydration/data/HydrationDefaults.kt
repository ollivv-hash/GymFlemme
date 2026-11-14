package fr.olivier.forme.feature.hydration.data

import android.content.ContentValues

/**
 * Drinks and containers offered from the start (V2.29): written by the migration 14 → 15, at the creation of the
 * database, and on restoring a backup older than v16. Fixed ids, so the containers can name their drink.
 */
object HydrationDefaults {
    val drinks: List<DrinkEntity> = listOf(
        DrinkEntity(id = 1, code = DrinkEntity.WATER, name = "Eau", icon = "water_full", hydrationCoefficient = 1.0, isCaloric = false, sortOrder = 0),
        DrinkEntity(id = 2, code = "sparkling_water", name = "Eau gazeuse", icon = "bubble_chart", hydrationCoefficient = 1.0, isCaloric = false, sortOrder = 1),
        DrinkEntity(id = 3, code = "coffee", name = "Café", icon = "coffee", hydrationCoefficient = 1.0, isCaloric = false, sortOrder = 2),
        DrinkEntity(id = 4, code = "tea", name = "Thé / tisane", icon = "emoji_food_beverage", hydrationCoefficient = 1.0, isCaloric = false, sortOrder = 3),
        DrinkEntity(id = 5, code = "juice", name = "Jus de fruits", icon = "local_bar", hydrationCoefficient = 1.0, isCaloric = true, sortOrder = 4),
        DrinkEntity(id = 6, code = "milk", name = "Lait", icon = "water_drop", hydrationCoefficient = 1.0, isCaloric = true, sortOrder = 5),
        DrinkEntity(id = 7, code = "soda", name = "Soda", icon = "local_drink", hydrationCoefficient = 1.0, isCaloric = true, sortOrder = 6),
        DrinkEntity(id = 8, code = "soup", name = "Soupe / bouillon", icon = "soup_kitchen", hydrationCoefficient = 1.0, isCaloric = true, sortOrder = 7),
        DrinkEntity(id = 9, code = "beer", name = "Bière", icon = "sports_bar", hydrationCoefficient = 0.0, isCaloric = true, sortOrder = 8),
        DrinkEntity(id = 10, code = "wine", name = "Vin / alcool", icon = "wine_bar", hydrationCoefficient = 0.0, isCaloric = true, sortOrder = 9),
    )

    val containers: List<ContainerEntity> = listOf(
        ContainerEntity(id = 1, name = "Verre", volumeMl = 250, defaultDrinkId = 1, sortOrder = 0),
        ContainerEntity(id = 2, name = "Tasse", volumeMl = 200, defaultDrinkId = 3, sortOrder = 1),
        ContainerEntity(id = 3, name = "Bouteille", volumeMl = 500, defaultDrinkId = 1, sortOrder = 2),
        ContainerEntity(id = 4, name = "Gourde", volumeMl = 750, defaultDrinkId = 1, isDefault = true, sortOrder = 3),
    )

    fun DrinkEntity.toValues() = ContentValues().apply {
        put("id", id)
        put("code", code)
        put("name", name)
        put("icon", icon)
        put("hydrationCoefficient", hydrationCoefficient)
        put("linkedFoodId", linkedFoodId)
        put("isCaloric", isCaloric)
        put("archived", archived)
        put("sortOrder", sortOrder)
    }

    fun ContainerEntity.toValues() = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("volumeMl", volumeMl)
        put("defaultDrinkId", defaultDrinkId)
        put("isDefault", isDefault)
        put("archived", archived)
        put("sortOrder", sortOrder)
    }
}
