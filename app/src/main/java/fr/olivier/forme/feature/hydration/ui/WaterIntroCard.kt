package fr.olivier.forme.feature.hydration.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import fr.olivier.forme.R
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.core.humor.rememberQuip
import fr.olivier.forme.feature.hydration.data.HydrationSettings
import fr.olivier.forme.feature.hydration.data.WaterToday
import fr.olivier.forme.feature.hydration.domain.HydrationMath

const val WATER_INTRO_TAG = "water_intro"
const val WATER_INTRO_START_TAG = "water_intro_start"
const val WATER_INTRO_LATER_TAG = "water_intro_later"

/** "08:00" from minutes after midnight. */
fun clock(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/**
 * First use of the hydration (V2.29), on the home screen and the water screen until answered: the goal computed,
 * the session bonus, the reminders (ticked; the notification permission is asked when needed). "C'est parti" or
 * "Plus tard"; no reminder before an answer.
 */
@Composable
fun WaterIntroCard(
    today: WaterToday,
    settings: HydrationSettings,
    onStart: (remindersOn: Boolean) -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var reminders by rememberSaveable { mutableStateOf(true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onStart(true) }
    val goal = today.goal
    Card(modifier.fillMaxWidth().testTag(WATER_INTRO_TAG)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(rememberQuip(Quip.WATER_INTRO_TITLE) ?: stringResource(R.string.neutral_water_intro_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.water_intro_body, HydrationMath.liters(goal.baseMl), settings.goal.bonusMl))
            if (!goal.hasWeight && goal.customMl == null) {
                Text(stringResource(R.string.water_no_weight), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = reminders, role = Role.Checkbox) { reminders = it },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = reminders, onCheckedChange = null)
                Text(
                    stringResource(R.string.water_intro_reminders, clock(settings.reminders.startMinutes), clock(settings.reminders.endMinutes)),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(stringResource(R.string.water_indicative), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val needsPermission = reminders && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else onStart(reminders)
                    },
                    modifier = Modifier.testTag(WATER_INTRO_START_TAG),
                ) { Text(stringResource(R.string.water_intro_start)) }
                TextButton(onClick = onLater, modifier = Modifier.testTag(WATER_INTRO_LATER_TAG)) { Text(stringResource(R.string.water_intro_later)) }
            }
        }
    }
}
