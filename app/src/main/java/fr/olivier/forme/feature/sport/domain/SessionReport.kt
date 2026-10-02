package fr.olivier.forme.feature.sport.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class CardioResult(
    val durationSec: Int,
    val distanceM: Int?,
    val pacePer500mSec: Int?,
    val caloriesDisplayed: Int?,
)

data class ReportSet(val work: WorkSet, val isWarmup: Boolean)

data class ExerciseReport(
    val exerciseId: Long,
    val name: String,
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
    val perSide: Boolean,
    val skipped: Boolean,
    val sets: List<ReportSet>,
    val cardio: CardioResult?,
    val newRecords: NewRecords,
)

data class PreviousComparison(val startedAt: LocalDateTime, val volume: SessionVolume)

data class SessionReport(
    val startedAt: LocalDateTime,
    val durationMin: Int?,
    val templateCode: String?,
    val templateName: String?,
    val feeling: Int?,
    val notes: String?,
    val exercises: List<ExerciseReport>,
    val volume: SessionVolume,
    val previous: PreviousComparison?,
) {
    val records get() = exercises.filter { it.newRecords.any }
}

/** Plain-text summary, copied to the clipboard; reused in V2 for sharing and Markdown export. */
object SessionTextFormatter {
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Fmt.locale)
    private val shortDate = DateTimeFormatter.ofPattern("dd/MM", Fmt.locale)

    fun format(report: SessionReport): String = buildString {
        val title = listOfNotNull(report.templateCode, report.templateName).joinToString(" · ").ifEmpty { "Séance libre" }
        appendLine("Séance du ${report.startedAt.format(dateFormat)} — $title")
        val meta = listOfNotNull(
            report.durationMin?.let { "Durée : $it min" },
            report.feeling?.let { "Ressenti : $it/5" },
        )
        if (meta.isNotEmpty()) appendLine(meta.joinToString(" · "))
        appendLine()
        report.exercises.forEach { appendLine("${it.name} : ${exerciseLine(it)}") }
        appendLine()
        appendLine(volumeLine(report.volume))
        report.previous?.let { prev ->
            val diff = report.volume.totalKg - prev.volume.totalKg
            val pct = if (prev.volume.totalKg > 0) " (${signed((diff / prev.volume.totalKg * 100).roundToInt().toDouble())} %)" else ""
            appendLine(
                "Comparaison avec la séance ${report.templateCode ?: ""} du ${prev.startedAt.format(shortDate)} : " +
                    "${signed(diff)} kg$pct",
            )
        }
        if (report.records.isNotEmpty()) {
            appendLine(
                "Records : " + report.records.joinToString(" ; ") { ex ->
                    listOfNotNull(
                        ex.newRecords.loadKg?.let { "charge max ${Fmt.kg(it)}" },
                        ex.newRecords.volumeKg?.let { "volume ${Fmt.kg(it)}" },
                    ).joinToString(", ", prefix = "${ex.name} — ")
                },
            )
        }
        report.notes?.takeIf { it.isNotBlank() }?.let { appendLine("Notes : $it") }
    }.trimEnd()

    fun exerciseLine(ex: ExerciseReport): String {
        if (ex.skipped) return "sauté"
        ex.cardio?.let { c ->
            return listOfNotNull(
                Fmt.mmss(c.durationSec),
                c.distanceM?.let { "${Fmt.number(it.toDouble())} m" },
                c.pacePer500mSec?.let { "${Fmt.mmss(it)} /500 m" },
                c.caloriesDisplayed?.let { "$it kcal" },
            ).joinToString(" · ")
        }
        val working = ex.sets.filterNot { it.isWarmup }.map { it.work }
        if (working.isEmpty()) return "non réalisé"
        val warmups = ex.sets.count { it.isWarmup }
        val body = working.joinToString(" · ") { setLabel(ex.type, ex.perSide, it) }
        return if (warmups > 0) "$body (+ $warmups échauff.)" else body
    }

    fun setLabel(type: ExerciseType, perSide: Boolean, set: WorkSet): String {
        val base = when (type) {
            ExerciseType.WEIGHTED -> "${Fmt.kg(set.loadKg ?: 0.0)} × ${set.reps ?: 0}"
            ExerciseType.BODYWEIGHT ->
                if ((set.loadKg ?: 0.0) > 0) "${set.reps ?: 0} reps (+${Fmt.kg(set.loadKg!!)})" else "${set.reps ?: 0} reps"
            ExerciseType.TIMED -> Fmt.duration(set.durationSec ?: 0) + if (perSide) "/côté" else ""
            ExerciseType.CARDIO -> Fmt.duration(set.durationSec ?: 0)
        }
        return set.rir?.let { "$base (RIR $it)" } ?: base
    }

    fun volumeLine(volume: SessionVolume): String {
        val parts = mutableListOf<String>()
        val groups = volume.kgByGroup.entries.joinToString(", ") { "${it.key.label} ${Fmt.kg(it.value)}" }
        parts += "Volume total : ${Fmt.kg(volume.totalKg)}" + if (groups.isNotEmpty()) " ($groups)" else ""
        if (volume.bodyweightReps > 0) parts += "${volume.bodyweightReps} reps au poids du corps"
        if (volume.timedSec > 0) parts += "${Fmt.duration(volume.timedSec)} de gainage"
        return parts.joinToString(" · ")
    }

    private fun signed(value: Double) = (if (value >= 0) "+" else "") + Fmt.number(value)
}
