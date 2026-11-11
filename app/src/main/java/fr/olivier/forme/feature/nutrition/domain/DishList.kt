package fr.olivier.forme.feature.nutrition.domain

import java.text.Normalizer
import java.time.Instant

/** Order of Mes plats (V2.26), after the pinned dishes. */
enum class DishSort { MOST_USED, RECENT, ALPHABETICAL }

/** What the list needs of a dish. */
data class DishItem(val id: Long, val name: String, val pinned: Boolean, val useCount: Int, val lastUsedAt: Instant?)

object DishList {
    /** Lower case, without accents: "Pâtes" matches "pates". */
    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun matches(name: String, query: String): Boolean = query.isBlank() || fold(name).contains(fold(query.trim()))

    /** The dishes matching [query]: pinned first, then by [sort] (ties: A → Z). */
    fun <T> arrange(dishes: List<T>, sort: DishSort, query: String, item: (T) -> DishItem): List<T> {
        val byName = compareBy<T> { fold(item(it).name) }
        val order = when (sort) {
            DishSort.MOST_USED -> compareByDescending<T> { item(it).useCount }.then(byName)
            DishSort.RECENT -> compareByDescending<T> { item(it).lastUsedAt ?: Instant.EPOCH }.then(byName)
            DishSort.ALPHABETICAL -> byName
        }
        return dishes.filter { matches(item(it).name, query) }.sortedWith(compareByDescending<T> { item(it).pinned }.then(order))
    }
}
