package fr.olivier.forme.feature.sport.ui.home.bento

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import fr.olivier.forme.R
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.theme.BentoDims
import fr.olivier.forme.ui.theme.BentoType
import fr.olivier.forme.ui.theme.LocalBento
import java.util.Locale
import kotlin.math.roundToInt

const val SESSION_TILE_TAG = "bento_session"
const val SESSION_START_TAG = "bento_session_start"
const val LEVEL_TILE_TAG = "bento_level"

/** "2 012": grouped by a narrow no-break space, as in French. */
internal fun grouped(value: Int): String = "%,d".format(Locale.FRANCE, value).replace(' ', ' ').replace(' ', ' ')

// --- Header ---------------------------------------------------------------------------------------------------

/** "Salut {prénom}", the greeting (or welcome back) of the humour catalogue, the AI button (settings: nav bar, V2.23). */
@Composable
fun HomeHeader(
    hello: String?,
    greeting: String?,
    missedGoal: String?,
    onAnalyse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBento.current
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            hello?.let { Text(it, style = BentoType.label.copy(fontSize = BentoType.body.fontSize), color = c.textSecondary) }
            Text(greeting.orEmpty(), style = BentoType.greeting, color = c.text)
            missedGoal?.let { Text(it, style = BentoType.body, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp)) }
        }
        SquareIconButton(fr.olivier.forme.R.drawable.ic_auto_awesome, "Analyse IA du jour", onAnalyse, Modifier.padding(start = 8.dp))
    }
}

// --- Session ------------------------------------------------------------------------------------------------------

/**
 * The next session (or the one in progress): [title] of the proposed template, null without rotation; [week] done
 * / goal this week.
 */
data class SessionTileState(
    val running: Boolean = false,
    val title: String? = null,
    val week: Pair<Int, Int>? = null,
    val exercises: Int? = null,
    val typicalMinutes: Int? = null,
)

/** The details line: "5 exercices · ~40 min". */
fun SessionTileState.details(): String? = listOfNotNull(
    exercises?.takeIf { it > 0 }?.let { "$it exercice${if (it > 1) "s" else ""}" },
    typicalMinutes?.let { "~$it min" },
).joinToString(" · ").ifEmpty { null }

/**
 * Accent tile over the whole width. The button starts the proposed session (resumes the one in progress); the rest
 * of the tile opens the choice of another session.
 */
@Composable
fun SessionTile(
    state: SessionTileState,
    startLabel: String,
    onStart: () -> Unit,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBento.current
    val label = if (state.running) "Séance en cours" else if (state.title != null) "Prochaine séance" else "Nouvelle séance"
    val title = state.title ?: "Séance libre"
    val details = if (state.running) null else state.details()
    val description = listOfNotNull(
        label, title,
        state.week?.takeIf { !state.running }?.let { "semaine : ${it.first} séances sur ${it.second}" },
        details?.replace("~", "environ "),
    ).joinToString(", ")
    BentoTile(
        color = c.accent,
        modifier = modifier.fillMaxWidth().testTag(SESSION_TILE_TAG),
        description = description,
        padding = BentoDims.sessionPadding,
        onClick = if (state.running) onStart else onChoose,
        onClickLabel = if (state.running) "reprendre" else "choisir une autre séance",
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label.uppercase(Locale.FRENCH), style = BentoType.labelCaps, color = c.onAccent)
                Text(title, style = BentoType.session, color = c.onAccent)
            }
            state.week?.takeIf { !state.running }?.let { (done, goal) ->
                Text(
                    "Sem. $done / $goal",
                    style = BentoType.labelStrong,
                    color = c.onAccent,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accentChip).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.size(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(details.orEmpty(), style = BentoType.body, color = c.onAccent, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(c.onAccent)
                    .clickable(role = Role.Button, onClick = onStart)
                    .testTag(SESSION_START_TAG)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (state.running) "Reprendre" else startLabel, style = BentoType.button, color = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}

// --- Calories and proteins ---------------------------------------------------------------------------------------

/** [kcal] null: no meal today; [target] null: no calorie goal. */
data class CaloriesTileState(val kcal: Int? = null, val target: Int? = null)

/** [grams] null: no meal today; [target] null: no weight logged yet. */
data class ProteinTileState(val grams: Int? = null, val target: Int? = null) {
    val reached get() = grams != null && target != null && grams >= target
}

@Composable
private fun NumberTile(
    label: String,
    value: String,
    sub: String,
    fraction: Float?,
    description: String,
    contrast: Boolean,
    barColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalBento.current
    val bg = if (contrast) c.contrastTile else c.tile
    val text = if (contrast) c.onContrastTile else c.text
    val secondary = if (contrast) c.onContrastTileSecondary else c.textSecondary
    val track = if (contrast) c.contrastTrack else c.track
    BentoTile(color = bg, modifier = modifier.heightIn(min = BentoDims.tileHeight), description = description, onClick = onClick) {
        Text(label, style = BentoType.label, color = secondary)
        Spacer(Modifier.weight(1f).defaultMinSize(minHeight = 12.dp))
        Text(value, style = BentoType.number, color = text)
        Text(sub, style = BentoType.label, color = secondary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.weight(1f).defaultMinSize(minHeight = 12.dp))
        if (fraction != null) ProgressTrack(fraction, track, barColor)
    }
}

@Composable
fun CaloriesTile(state: CaloriesTileState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    val kcal = state.kcal
    val target = state.target
    val sub = when {
        kcal == null -> "Ajouter un repas"
        target == null -> "définis ton objectif"
        else -> "sur ${grouped(target)} kcal"
    }
    NumberTile(
        label = "Calories",
        value = kcal?.let(::grouped) ?: "—",
        sub = sub,
        fraction = if (kcal != null && target != null && target > 0) kcal.toFloat() / target else null,
        description = when {
            kcal == null -> "Calories, aucun repas aujourd'hui, ajouter un repas"
            target == null -> "Calories, ${grouped(kcal)} kilocalories, objectif à définir"
            else -> "Calories, ${grouped(kcal)} sur ${grouped(target)} kilocalories"
        },
        contrast = true,
        barColor = c.caloriesBar,
        onClick = onOpen,
        modifier = modifier,
    )
}

@Composable
fun ProteinTile(state: ProteinTileState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    val grams = state.grams
    val target = state.target
    val sub = when {
        grams == null -> "Ajouter un repas"
        target == null -> "ajoute une pesée"
        state.reached -> "objectif $target g atteint"
        else -> "objectif $target g"
    }
    NumberTile(
        label = "Protéines",
        value = grams?.let { "$it g" } ?: "—",
        sub = sub,
        fraction = if (grams != null && target != null && target > 0) grams.toFloat() / target else null,
        description = when {
            grams == null -> "Protéines, aucun repas aujourd'hui, ajouter un repas"
            target == null -> "Protéines, $grams grammes, ajoute une pesée pour l'objectif"
            else -> "Protéines, $grams grammes sur $target" + if (state.reached) ", objectif atteint" else ""
        },
        contrast = false,
        barColor = if (state.reached) c.accent else c.strong,
        onClick = onOpen,
        modifier = modifier,
    )
}

// --- Challenge ---------------------------------------------------------------------------------------------------

/** "Défi de novembre", "Douze coups", 5 / 12 "séances" (measures already formatted, rower in km). */
data class ChallengeTileState(
    val month: String,
    val name: String,
    val value: Double,
    val goal: Double,
    val valueText: String,
    val goalText: String,
    val unit: String,
    val done: Boolean,
)

@Composable
fun ChallengeTile(state: ChallengeTileState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    val progress = "${state.valueText} / ${state.goalText} ${state.unit}"
    BentoTile(
        color = c.tile,
        modifier = modifier,
        description = "${state.month}, ${state.name}, " + if (state.done) "réussi" else progress,
        onClick = onOpen,
    ) {
        Text(state.month, style = BentoType.label, color = c.textSecondary)
        Text(state.name, style = BentoType.tileTitle, color = c.text, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.size(10.dp))
        val goal = state.goal.roundToInt()
        if (goal in 1..15 && state.goal == goal.toDouble()) {
            SegmentedProgress(state.value.toInt().coerceAtMost(goal), goal, c.strong, c.track)
        } else {
            ProgressTrack(if (state.goal > 0) (state.value / state.goal).toFloat() else 0f, c.track, c.strong, height = 8.dp)
        }
        Text(if (state.done) "Réussi ✓" else progress, style = BentoType.label, color = c.textSecondary, modifier = Modifier.padding(top = 10.dp))
    }
}

// --- Supplements (creatine) ---------------------------------------------------------------------------------------

data class SupplementItem(val id: Long, val label: String, val taken: Boolean)

/**
 * "Créatine" (only creatine active) or "Compléments": the creatine streak, then one box per active supplement (3 at
 * most, the others in Nutrition).
 */
data class SupplementsTileState(val title: String, val streak: String?, val items: List<SupplementItem>, val more: Int = 0)

@Composable
fun SupplementsTile(state: SupplementsTileState, onToggle: (SupplementItem, Boolean) -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    BentoTile(color = c.tile, modifier = modifier) {
        Text(state.title, style = BentoType.label, color = c.textSecondary)
        state.streak?.let { Text(it, style = BentoType.tileTitle, color = c.text, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.size(4.dp))
        state.items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = BentoDims.touch)
                    .toggleable(value = item.taken, role = Role.Checkbox) { onToggle(item, it) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = item.taken,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = c.check, checkmarkColor = c.onCheck, uncheckedColor = c.textSecondary),
                )
                Text(item.label, style = BentoType.body, color = c.text, modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (state.more > 0) {
            Text(
                "+${state.more} dans Nutrition",
                style = BentoType.label,
                color = c.textSecondary,
                modifier = Modifier.heightIn(min = BentoDims.touch).clickable(role = Role.Button, onClick = onMore).padding(top = 12.dp),
            )
        }
    }
}

// --- Water (V2.29) ------------------------------------------------------------------------------------------------

const val WATER_TILE_TAG = "bento_water"
const val WATER_ADD_TAG = "bento_water_add"

/**
 * Today's water: [countedMl] / [goalMl], [bonusMl] of the session when it applies, [line] the secondary line of the
 * humour catalogue; the "+" adds [defaultMl] of [defaultDrink] (the default container).
 */
data class WaterTileState(
    val countedMl: Int,
    val goalMl: Int,
    val bonusMl: Int = 0,
    val line: String? = null,
    val defaultMl: Int? = null,
    val defaultDrink: String? = null,
)

/** Half-width tile: tap opens the water screen; "+" adds the default container, a long press opens the choice. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WaterTile(state: WaterTileState, onOpen: () -> Unit, onAdd: () -> Unit, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val counted = HydrationMath.litersNumber(state.countedMl)
    val goal = HydrationMath.litersNumber(state.goalMl)
    val description = listOfNotNull(
        res.getString(R.string.water_tile_description, HydrationMath.litersSpoken(state.countedMl), goal),
        state.bonusMl.takeIf { it > 0 }?.let { res.getString(R.string.water_tile_bonus_spoken, it) },
        state.line,
    ).joinToString(", ")
    BentoTile(
        color = c.tile,
        modifier = modifier.heightIn(min = BentoDims.tileHeight).testTag(WATER_TILE_TAG),
        description = description,
        onClick = onOpen,
        onClickLabel = res.getString(R.string.water_open),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(res.getString(R.string.water_title), style = BentoType.label, color = c.textSecondary)
                if (state.bonusMl > 0) {
                    Text(res.getString(R.string.water_tile_bonus, state.bonusMl), style = BentoType.label, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
                }
            }
            val addLabel = state.defaultMl?.let { res.getString(R.string.water_add_default, it, state.defaultDrink.orEmpty()) } ?: res.getString(R.string.water_add)
            Box(
                Modifier
                    .size(BentoDims.touchMin)
                    .clip(RoundedCornerShape(50))
                    .background(c.accent)
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = addLabel,
                        onLongClickLabel = res.getString(R.string.water_add_choose),
                        onLongClick = onChoose,
                        onClick = onAdd,
                    )
                    .semantics { contentDescription = addLabel }
                    .testTag(WATER_ADD_TAG),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.weight(1f).defaultMinSize(minHeight = 8.dp))
        Text(
            buildAnnotatedString {
                withStyle(BentoType.number.toSpanStyle()) { append(counted) }
                withStyle(BentoType.bodyStrong.toSpanStyle()) { append(" / $goal L") }
            },
            color = c.text,
        )
        state.line?.let { Text(it, style = BentoType.label, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
        Spacer(Modifier.weight(1f).defaultMinSize(minHeight = 8.dp))
        ProgressTrack(if (state.goalMl > 0) state.countedMl.toFloat() / state.goalMl else 0f, c.track, c.accent)
    }
}

// --- Level --------------------------------------------------------------------------------------------------------

data class LevelTileState(val number: Int, val title: String, val points: Int, val floor: Int, val next: Int?)

@Composable
fun LevelTile(state: LevelTileState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    val next = state.next
    val progress = if (next != null) "${grouped(state.points)} / ${grouped(next)}" else "${grouped(state.points)} points"
    BentoTile(
        color = c.tile,
        modifier = modifier.fillMaxWidth().testTag(LEVEL_TILE_TAG),
        description = "Niveau ${state.number}, ${state.title}, " + if (next != null) "${grouped(state.points)} points sur ${grouped(next)}" else "niveau maximal",
        padding = 14.dp,
        onClick = onOpen,
        onClickLabel = "ouvrir les badges",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(BentoDims.touch).clip(RoundedCornerShape(BentoDims.squareRadius)).background(c.levelBar),
                contentAlignment = Alignment.Center,
            ) { Text("${state.number}", style = BentoType.levelNumber, color = c.onStrong) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.title, style = BentoType.bodyStrong, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (next != null) {
                    ProgressTrack((state.points - state.floor).toFloat() / (next - state.floor).coerceAtLeast(1), c.track, c.levelBar, height = 5.dp)
                }
            }
            Text(progress, style = BentoType.label, color = c.textSecondary)
        }
    }
}

// --- Deload advice ------------------------------------------------------------------------------------------------

/** Deload advice (V2.3), over the whole width, only while it applies. */
@Composable
fun DeloadTile(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    BentoTile(color = c.tile, modifier = modifier.fillMaxWidth()) {
        Text("Semaine de décharge conseillée", style = BentoType.tileTitle, color = c.text)
        Text(message, style = BentoType.body, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
        Text(
            "Compris",
            style = BentoType.button,
            color = c.text,
            modifier = Modifier
                .align(Alignment.End)
                .heightIn(min = BentoDims.touch)
                .clickable(role = Role.Button, onClick = onDismiss)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

/** Used by the previews and tests: one width of the mock-up. */
internal val MockupWidth = Modifier.width(390.dp)

/** Formats a measure of a challenge like the Badges screen (rower in km, else a whole number). */
internal fun measure(rower: Boolean, value: Double): String =
    if (rower) Fmt.number(kotlin.math.floor(value / 100) / 10, 1) else Fmt.number(kotlin.math.floor(value), 0)
