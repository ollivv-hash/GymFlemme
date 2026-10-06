package fr.olivier.forme.feature.ai.domain

import fr.olivier.forme.feature.nutrition.domain.DayTotals
import fr.olivier.forme.feature.nutrition.domain.KcalTarget
import fr.olivier.forme.feature.nutrition.domain.NutritionWeek
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import fr.olivier.forme.feature.sport.domain.SessionReport
import fr.olivier.forme.feature.sport.domain.SessionTextFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class AiProfile(
    val age: Int? = null,
    val sex: String? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val weightAverage7Kg: Double? = null,
    /** Change of the 7-day average over the last 4 weeks; null without enough weigh-ins. */
    val weightChange4wKg: Double? = null,
    val goal: String? = null,
    val proteinTargetG: Double? = null,
    val restDayKcal: Int? = null,
    val hrMax: Int? = null,
)

data class AiHeart(
    val avgBpm: Int,
    val maxBpm: Int,
    /** Minutes in zones 1..5. */
    val zoneMinutes: List<Int>,
    val kcal: Int?,
    /** "estimation par la FC" or "mesurées par la montre". */
    val kcalLabel: String?,
)

/** What the app knows about one exercise beyond the sets: last time, estimated 1RM, plateau. */
data class AiExerciseExtra(
    val exerciseId: Long,
    val lastTime: String? = null,
    val oneRepMaxKg: Double? = null,
    val plateauSessions: Int? = null,
)

data class AiSession(val report: SessionReport, val heart: AiHeart?, val extras: List<AiExerciseExtra>)

data class AiMeal(val label: String, val proteinG: Double, val kcal: Double, val foods: List<String>)

data class AiNutrition(
    val totals: DayTotals,
    val proteinTargetG: Double?,
    val kcalTarget: KcalTarget?,
    val meals: List<AiMeal>,
    val wheyDoses: Int,
    val creatineTaken: Boolean,
)

data class AiWeek(
    val weekStart: LocalDate,
    val sessions: Int,
    val sessionsGoal: Int,
    val setsByGroup: Map<MuscleGroup, Int>,
    val minSets: Int,
    val maxSets: Int,
    val nutrition: NutritionWeek?,
    val measuredSessions: Int,
    val sessionKcal: Int,
)

data class AiDay(
    val date: LocalDate,
    val exportedAt: LocalDateTime,
    val profile: AiProfile,
    val sessions: List<AiSession>,
    val nutrition: AiNutrition,
    val week: AiWeek,
)

/** Builds the Markdown file handed to the AI app: instructions first, then the day's data. */
object AnalysisMarkdown {
    private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Fmt.locale)
    private val shortDay = DateTimeFormatter.ofPattern("EEEE d MMM", Fmt.locale)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Fmt.locale)
    private val shortDate = DateTimeFormatter.ofPattern("dd/MM", Fmt.locale)

    /** Default instructions; editable in Config → Analyse IA. */
    val DEFAULT_PROMPT = """
        Tu es mon coach musculation et nutrition. Analyse ma journée à partir des données ci-dessous, exportées de mon app Forme.

        Réponds en français, de façon directe et concise (pas d'introduction ni de formule de politesse), avec exactement ces sections :
        1. **Résumé** : 3 lignes maximum sur la journée.
        2. **Séance** : ce qui progresse, ce qui stagne ou recule (charges, reps, RIR, fréquence cardiaque), chiffres à l'appui. S'il n'y a pas de séance, dis seulement si c'est cohérent avec ma semaine.
        3. **Nutrition** : écart aux objectifs (protéines, calories) et ce qu'il me reste à manger aujourd'hui, avec des aliments et des quantités concrets.
        4. **Conseils** : 3 actions maximum, concrètes et chiffrées, pour la prochaine séance et pour demain.

        Règles :
        - Base-toi uniquement sur ces données ; si une information manque, dis-le en une phrase au lieu de l'inventer.
        - Tiens compte de mon objectif (prise de masse, maintien ou sèche) et des tendances de la semaine.
        - Les calories de séance sont estimées à partir de la fréquence cardiaque (formule de Keytel), sauf mention « mesurées par la montre ».
        - La journée n'est peut-être pas terminée : regarde l'heure d'export.
        - Pas de diagnostic médical ; signale seulement si quelque chose mérite l'avis d'un professionnel.
    """.trimIndent()

    fun fileName(date: LocalDate) = "forme-analyse-$date.md"

    fun build(day: AiDay, prompt: String = DEFAULT_PROMPT): String = buildString {
        appendLine("# Forme · analyse du ${day.date.format(dayFormat)}")
        appendLine()
        appendLine("_Données exportées le ${day.exportedAt.format(shortDate)} à ${day.exportedAt.format(timeFormat)}._")
        appendLine()
        appendLine("## Consignes")
        appendLine()
        appendLine(prompt.trim())
        appendLine()
        profile(day.profile)
        if (day.sessions.isEmpty()) {
            appendLine("## Séance du jour")
            appendLine()
            appendLine("Pas de séance ce jour-là.")
            appendLine()
        } else {
            day.sessions.forEach { session(it) }
        }
        nutrition(day.nutrition)
        week(day.week)
    }.trimEnd() + "\n"

    private fun StringBuilder.profile(p: AiProfile) {
        appendLine("## Profil")
        appendLine()
        listOfNotNull(p.age?.let { "$it ans" }, p.sex, p.heightCm?.let { "$it cm" })
            .takeIf { it.isNotEmpty() }?.let { appendLine("- " + it.joinToString(" · ")) }
        p.weightKg?.let { w ->
            val details = listOfNotNull(
                p.weightAverage7Kg?.let { "moyenne 7 j : ${Fmt.kg(it)}" },
                p.weightChange4wKg?.let { "${signed(it)} kg en 4 semaines" },
            )
            appendLine("- Poids : ${Fmt.kg(w)}" + if (details.isEmpty()) "" else " (${details.joinToString(" ; ")})")
        }
        appendLine(
            "- Objectif : " + listOfNotNull(
                p.goal ?: "non défini",
                p.proteinTargetG?.let { "protéines ${it.roundToInt()} g/j" },
                p.restDayKcal?.let { "calories jour de repos ${kcal(it)} kcal (+ dépense des séances les jours d'entraînement)" },
            ).joinToString(" · "),
        )
        p.hrMax?.let { appendLine("- FC max : $it bpm") }
        appendLine()
    }

    private fun StringBuilder.session(s: AiSession) {
        val r = s.report
        val title = listOfNotNull(r.templateCode, r.templateName).joinToString(" · ").ifEmpty { "Séance libre" }
        appendLine("## Séance du jour : $title")
        appendLine()
        appendLine(
            "- " + listOfNotNull(
                "début ${r.startedAt.format(timeFormat)}",
                r.durationMin?.let { "$it min" },
                r.feeling?.let { "ressenti $it/5" },
            ).joinToString(" · "),
        )
        appendLine("- Exercices (séries de travail ; RIR = répétitions en réserve) :")
        val extras = s.extras.associateBy { it.exerciseId }
        r.exercises.forEach { ex ->
            val extra = extras[ex.exerciseId]
            val notes = listOfNotNull(
                extra?.lastTime?.let { "dernière fois : $it" },
                extra?.oneRepMaxKg?.let { "1RM estimé ${Fmt.kg((it * 2).roundToInt() / 2.0)}" },
                extra?.plateauSessions?.let { "stagnation depuis $it séances" },
            )
            appendLine("  - ${ex.name} : ${SessionTextFormatter.exerciseLine(ex)}" + if (notes.isEmpty()) "" else " — ${notes.joinToString(" · ")}")
        }
        appendLine("- ${SessionTextFormatter.volumeLine(r.volume)}")
        r.previous?.let { prev ->
            val diff = r.volume.totalKg - prev.volume.totalKg
            appendLine("- Séance précédente du même modèle (${prev.startedAt.format(shortDate)}) : ${Fmt.kg(prev.volume.totalKg)} (${signed(diff)} kg)")
        }
        if (r.records.isNotEmpty()) {
            appendLine(
                "- Records battus : " + r.records.joinToString(" ; ") { ex ->
                    listOfNotNull(
                        ex.newRecords.loadKg?.let { "charge max ${Fmt.kg(it)}" },
                        ex.newRecords.volumeKg?.let { "volume ${Fmt.kg(it)}" },
                    ).joinToString(", ", prefix = "${ex.name} : ")
                },
            )
        }
        s.heart?.let { h ->
            val zones = h.zoneMinutes.withIndex().filter { it.value > 0 }.joinToString(", ") { "Z${it.index + 1} ${it.value} min" }
            appendLine(
                "- Fréquence cardiaque : moyenne ${h.avgBpm} bpm, max ${h.maxBpm} bpm" +
                    (if (zones.isNotEmpty()) " · $zones" else "") +
                    (h.kcal?.let { " · $it kcal${h.kcalLabel?.let { l -> " ($l)" } ?: ""}" } ?: ""),
            )
        }
        r.notes?.takeIf { it.isNotBlank() }?.let { appendLine("- Notes : $it") }
        appendLine()
    }

    private fun StringBuilder.nutrition(n: AiNutrition) {
        appendLine("## Nutrition du jour")
        appendLine()
        appendLine("- Protéines : ${n.totals.proteinG.roundToInt()} g" + (n.proteinTargetG?.let { " / ${it.roundToInt()} g" } ?: ""))
        val target = n.kcalTarget
        appendLine(
            "- Calories : ${kcal(n.totals.kcal.roundToInt())} kcal" + if (target != null) {
                " / ${kcal(target.totalKcal)} kcal" + if (target.sessionKcal > 0) {
                    " (repos ${kcal(target.baseKcal)} + séances ${target.sessionKcal})"
                } else {
                    ""
                }
            } else {
                ""
            },
        )
        appendLine("- Glucides ${n.totals.carbsG.roundToInt()} g · lipides ${n.totals.fatG.roundToInt()} g")
        appendLine("- Whey : ${if (n.wheyDoses == 0) "aucune dose" else "${n.wheyDoses} dose${if (n.wheyDoses > 1) "s" else ""}"} · créatine : ${if (n.creatineTaken) "prise" else "non prise"}")
        if (n.meals.isEmpty()) {
            appendLine("- Aucun repas saisi.")
        } else {
            appendLine("- Repas :")
            n.meals.forEach { m ->
                appendLine("  - ${m.label} (${m.proteinG.roundToInt()} g prot., ${m.kcal.roundToInt()} kcal) : ${m.foods.joinToString(" ; ")}")
            }
        }
        appendLine()
    }

    private fun StringBuilder.week(w: AiWeek) {
        appendLine("## Semaine (depuis le ${w.weekStart.format(shortDay)})")
        appendLine()
        appendLine("- Séances : ${w.sessions} / ${w.sessionsGoal}")
        val groups = MuscleGroup.entries.filter { it != MuscleGroup.CARDIO && (it != MuscleGroup.AUTRE || (w.setsByGroup[it] ?: 0) > 0) }
        appendLine(
            "- Séries par groupe musculaire (repère ${w.minSets} à ${w.maxSets} par semaine) : " +
                groups.joinToString(" · ") { "${it.label} ${w.setsByGroup[it] ?: 0}" },
        )
        w.nutrition?.takeIf { it.loggedDays > 0 }?.let { n ->
            appendLine(
                "- Nutrition : " + listOfNotNull(
                    "${n.loggedDays} jour${if (n.loggedDays > 1) "s" else ""} saisi${if (n.loggedDays > 1) "s" else ""} sur ${n.elapsedDays}",
                    n.avgProteinG?.let { "protéines ${it.roundToInt()} g/j" },
                    n.proteinTargetDays?.let { "objectif protéines atteint $it/${n.loggedDays}" },
                    n.avgKcal?.let { "${kcal(it.roundToInt())} kcal/j" },
                    n.kcalTargetDays?.let { "dans l'objectif calories $it/${n.loggedDays}" },
                    "whey ${n.wheyDays}/${n.elapsedDays}",
                    "créatine ${n.creatineDays}/${n.elapsedDays}",
                ).joinToString(" · "),
            )
        }
        if (w.measuredSessions > 0) {
            appendLine("- Fréquence cardiaque : ${w.measuredSessions} séance(s) mesurée(s), ${kcal(w.sessionKcal)} kcal dépensées")
        }
    }

    private fun kcal(value: Int) = Fmt.number(value.toDouble(), 0)
    private fun signed(value: Double) = (if (value >= 0) "+" else "") + Fmt.number(value)
}
