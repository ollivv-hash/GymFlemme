package fr.olivier.forme.feature.hydration.ui

import fr.olivier.forme.R

/**
 * Material Symbols offered for the drinks (V2.29), by name as stored in `drink.icon`; an unknown name shows the
 * water glass.
 */
object DrinkIcons {
    val all: Map<String, Int> = linkedMapOf(
        "water_full" to R.drawable.ic_drink_water_full,
        "bubble_chart" to R.drawable.ic_drink_bubble_chart,
        "coffee" to R.drawable.ic_drink_coffee,
        "emoji_food_beverage" to R.drawable.ic_drink_emoji_food_beverage,
        "local_bar" to R.drawable.ic_drink_local_bar,
        "water_drop" to R.drawable.ic_drink_water_drop,
        "local_drink" to R.drawable.ic_drink_local_drink,
        "soup_kitchen" to R.drawable.ic_drink_soup_kitchen,
        "sports_bar" to R.drawable.ic_drink_sports_bar,
        "wine_bar" to R.drawable.ic_drink_wine_bar,
    )

    fun of(name: String): Int = all[name] ?: R.drawable.ic_drink_water_full
}
