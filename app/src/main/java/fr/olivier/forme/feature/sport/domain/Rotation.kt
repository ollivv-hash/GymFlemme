package fr.olivier.forme.feature.sport.domain

data class RotationTemplate(val id: Long, val orderInRotation: Int?, val archived: Boolean)

/**
 * Proposed template = the one following the last finished session whose template is still in the
 * rotation (non-archived, orderInRotation set). Templates outside the rotation (e.g. lower body) are
 * skipped, so they never shift it. Wraps around; falls back to the first template of the rotation.
 */
object RotationResolver {
    fun next(templates: List<RotationTemplate>, finishedTemplateIdsMostRecentFirst: List<Long>): Long? {
        val rotation = templates
            .filter { !it.archived && it.orderInRotation != null }
            .sortedWith(compareBy<RotationTemplate> { it.orderInRotation }.thenBy { it.id })
            .map { it.id }
        if (rotation.isEmpty()) return null
        val last = finishedTemplateIdsMostRecentFirst.firstOrNull { it in rotation } ?: return rotation.first()
        return rotation[(rotation.indexOf(last) + 1) % rotation.size]
    }
}
