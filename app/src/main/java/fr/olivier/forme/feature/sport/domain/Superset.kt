package fr.olivier.forme.feature.sport.domain

/**
 * Supersets are 2 adjacent exercises sharing a group number. These rules keep groups valid
 * after links, unlinks and reorders, and decide when the rest timer starts.
 */
object SupersetRules {
    const val SIZE = 2

    /**
     * Keeps a group only if it has exactly [SIZE] adjacent members; renumbers groups 1, 2, 3…
     * in order of appearance. [groups] is indexed by position.
     */
    fun normalize(groups: List<Int?>): List<Int?> {
        val result = MutableList<Int?>(groups.size) { null }
        var next = 1
        var start = 0
        while (start < groups.size) {
            val g = groups[start]
            var end = start
            while (end + 1 < groups.size && g != null && groups[end + 1] == g) end++
            // A run of consecutive equal groups is a valid superset only if it has exactly SIZE members.
            if (g != null && end - start + 1 == SIZE) {
                (start..end).forEach { result[it] = next }
                next++
            }
            start = end + 1
        }
        return result
    }

    /** Links position [index] with the next one, or unlinks them if already linked. */
    fun toggleWithNext(groups: List<Int?>, index: Int): List<Int?> {
        if (index !in groups.indices || index + 1 !in groups.indices) return normalize(groups)
        val linked = groups[index] != null && groups[index] == groups[index + 1]
        val updated = groups.toMutableList()
        if (linked) {
            updated[index] = null
            updated[index + 1] = null
        } else {
            // An exercise belongs to one superset at most: break its previous links first.
            listOf(index, index + 1).forEach { pos ->
                val old = updated[pos] ?: return@forEach
                updated.indices.filter { updated[it] == old }.forEach { updated[it] = null }
            }
            val fresh = (updated.filterNotNull().maxOrNull() ?: 0) + 1
            updated[index] = fresh
            updated[index + 1] = fresh
        }
        return normalize(updated)
    }

    /**
     * After a set of one superset member, the rest starts only when the partner has no set left to do
     * in this round: i.e. the member is the last of the pair, or the partner is finished.
     */
    fun restAfterSet(isLastOfPair: Boolean, partnerFinished: Boolean): Boolean = isLastOfPair || partnerFinished
}
