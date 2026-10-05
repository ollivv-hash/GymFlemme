package fr.olivier.forme.feature.heart.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.core.settings.HrDevice
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.heart.data.BleDevice
import fr.olivier.forme.feature.heart.data.BleHeartRateMonitor
import fr.olivier.forme.feature.heart.data.BleLink
import fr.olivier.forme.feature.heart.data.BleState
import fr.olivier.forme.feature.heart.data.HealthConnectAvailability
import fr.olivier.forme.feature.heart.data.HealthConnectProbe
import fr.olivier.forme.feature.heart.data.HealthConnectReader
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HealthConnectUi(
    val availability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val granted: Set<String> = emptySet(),
    val backgroundSupported: Boolean = false,
    val hours: Int = 3,
    val reading: Boolean = false,
    val probe: HealthConnectProbe? = null,
    val error: String? = null,
)

/** Lot 0 (V2.1 feasibility): Bluetooth heart rate broadcast from the watch and Health Connect reading. */
@HiltViewModel
class WatchTestViewModel @Inject constructor(
    private val ble: BleHeartRateMonitor,
    private val healthConnect: HealthConnectReader,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val bleState = ble.state
    val savedDevice = settings.hrDevice.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _hc = MutableStateFlow(HealthConnectUi())
    val hc = _hc.asStateFlow()

    val blePermissions get() = ble.requiredPermissions()
    val hcPermissions get() = healthConnect.readPermissions

    init {
        refreshHealthConnect()
    }

    fun scan(onlyHeartRate: Boolean) = ble.startScan(onlyHeartRate)
    fun stopScan() = ble.stopScan()
    fun disconnect() = ble.disconnect()

    fun connect(device: BleDevice) {
        ble.connect(device)
        viewModelScope.launch { settings.setHrDevice(HrDevice(device.address, device.name)) }
    }

    fun connectSaved() {
        val saved = savedDevice.value ?: return
        // Same search as during a session: found by name even if its broadcast address changed.
        ble.watchFor(BleDevice(saved.address, saved.name, null, advertisesHeartRate = true, bonded = false)) { moved ->
            viewModelScope.launch { settings.setHrDevice(HrDevice(moved.address, moved.name ?: saved.name)) }
        }
    }

    fun setHours(hours: Int) {
        _hc.value = _hc.value.copy(hours = hours)
    }

    fun refreshHealthConnect() = viewModelScope.launch {
        val availability = healthConnect.availability()
        _hc.value = _hc.value.copy(availability = availability)
        if (availability != HealthConnectAvailability.AVAILABLE) return@launch
        runCatching { healthConnect.grantedPermissions() }
            .onSuccess { _hc.value = _hc.value.copy(granted = it, backgroundSupported = healthConnect.backgroundReadSupported()) }
            .onFailure { _hc.value = _hc.value.copy(error = it.message) }
    }

    fun readHealthConnect() = viewModelScope.launch {
        _hc.value = _hc.value.copy(reading = true, error = null)
        val to = clock.instant()
        runCatching { healthConnect.probe(to.minus(Duration.ofHours(_hc.value.hours.toLong())), to) }
            .onSuccess { _hc.value = _hc.value.copy(probe = it, reading = false) }
            .onFailure { _hc.value = _hc.value.copy(error = it.message ?: it.javaClass.simpleName, reading = false) }
    }

    override fun onCleared() {
        ble.stopScan()
    }

    /** Plain-text report to paste back in the conversation. */
    fun report(): String = buildString {
        appendLine("Forme — test montre cardio")
        appendLine("Téléphone : ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val s = bleState.value
        appendLine("Bluetooth : ${if (!s.available) "indisponible" else if (!s.enabled) "désactivé" else "actif"}, autorisations ${if (ble.hasPermissions()) "OK" else "manquantes"}")
        appendLine("Montre mémorisée : ${savedDevice.value?.let { "${it.name} ${it.address}" } ?: "aucune"}")
        appendLine("Lien : ${describe(s.link)}")
        appendLine("Appareils vus : ${s.devices.size} (dont ${s.devices.count { it.advertisesHeartRate }} avec service FC)")
        s.devices.take(12).forEach { appendLine("  - ${it.name ?: "(sans nom)"} ${it.address} ${it.rssi ?: "-"} dBm${if (it.advertisesHeartRate) " FC" else ""}${if (it.bonded) " appairé" else ""}") }
        appendLine("Journal Bluetooth :")
        s.log.forEach { appendLine("  $it") }
        val h = _hc.value
        appendLine("Health Connect : ${h.availability}, lecture arrière-plan ${if (h.backgroundSupported) "supportée" else "non supportée"}")
        hcPermissions.forEach { appendLine("  ${if (it in h.granted) "✓" else "✗"} ${it.substringAfterLast('.')}") }
        h.probe?.let { appendLine(probeText(it)) }
        h.error?.let { appendLine("Erreur HC : $it") }
    }

    companion object {
        private val TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss").withZone(ZoneId.systemDefault())

        fun describe(link: BleLink): String = when (link) {
            BleLink.Idle -> "non connecté"
            is BleLink.Connecting -> "connexion à ${link.device.name ?: link.device.address}…"
            is BleLink.Connected -> "connecté à ${link.device.name ?: link.device.address}, ${link.notifications} mesures, dernière ${link.last?.bpm ?: "-"} bpm"
            is BleLink.Failed -> "échec : ${link.reason}"
            is BleLink.Reconnecting -> "reconnexion à ${link.device.name ?: link.device.address}…"
        }

        fun probeText(p: HealthConnectProbe): String = buildString {
            val bpm = p.samples.map { it.second }
            appendLine("Période : ${TIME.format(p.from)} → ${TIME.format(p.to)}")
            if (bpm.isEmpty()) {
                appendLine("FC : aucune mesure")
            } else {
                appendLine("FC : ${bpm.size} mesures dans ${p.heartRateRecords} enregistrements, intervalle médian ${p.medianIntervalSec ?: "-"} s")
                appendLine("     min ${bpm.min()} · moy ${bpm.average().toInt()} · max ${bpm.max()} bpm")
                appendLine("     de ${TIME.format(p.samples.first().first)} à ${TIME.format(p.samples.last().first)}")
            }
            appendLine("     sources : ${p.heartRateOrigins.joinToString().ifEmpty { "-" }}")
            appendLine("Calories actives : ${p.activeKcal.toInt()} kcal (${p.activeRecords} enr.) · totales : ${p.totalKcal.toInt()} kcal (${p.totalRecords} enr.)")
            append("     sources : ${p.calorieOrigins.joinToString().ifEmpty { "-" }}")
        }
    }
}

@Composable
fun WatchTestScreen(onBack: () -> Unit, viewModel: WatchTestViewModel = hiltViewModel()) {
    val ble by viewModel.bleState.collectAsStateWithLifecycle()
    val saved by viewModel.savedDevice.collectAsStateWithLifecycle()
    val hc by viewModel.hc.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var onlyHeartRate by remember { mutableStateOf(true) }

    val blePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.all { granted -> granted }) viewModel.scan(onlyHeartRate)
    }
    val hcPermissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refreshHealthConnect()
    }

    Scaffold(topBar = { FormeTopBar("Montre cardio", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        ) {
            Text(
                "Choisis ici ta montre : pendant les séances, Forme s'y connecte toute seule dès que la diffusion FC " +
                    "est active. Les deux tests ci-dessous servent aussi au diagnostic (« Copier le rapport »).",
                style = MaterialTheme.typography.bodyMedium,
            )

            SectionTitle("1. FC en direct (Bluetooth)")
            Text(
                "Sur la montre, active la diffusion de la fréquence cardiaque (partage FC / « Heart rate broadcast »), " +
                    "si besoin en lançant un entraînement. Puis lance la recherche ici.",
                style = MaterialTheme.typography.bodySmall,
            )
            BleSection(
                state = ble,
                saved = saved,
                onlyHeartRate = onlyHeartRate,
                onToggleFilter = { onlyHeartRate = !onlyHeartRate },
                onScan = {
                    if (viewModel.blePermissions.all { p -> ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED }) {
                        viewModel.scan(onlyHeartRate)
                    } else {
                        blePermissionLauncher.launch(viewModel.blePermissions)
                    }
                },
                onStop = viewModel::stopScan,
                onConnect = viewModel::connect,
                onConnectSaved = viewModel::connectSaved,
                onDisconnect = viewModel::disconnect,
            )

            SectionTitle("2. Health Connect (après séance)")
            HealthConnectSection(
                ui = hc,
                permissions = viewModel.hcPermissions,
                onGrant = { hcPermissionLauncher.launch(viewModel.hcPermissions) },
                onHours = viewModel::setHours,
                onRead = viewModel::readHealthConnect,
            )

            SectionTitle("Journal Bluetooth")
            Text(
                ble.log.takeLast(20).joinToString("\n").ifEmpty { "(vide)" },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )

            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Rapport montre", viewModel.report()))
                    scope.launch { snackbar.showSnackbar("Rapport copié") }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            ) { Text("Copier le rapport") }
        }
    }
}

@Composable
private fun BleSection(
    state: BleState,
    saved: HrDevice?,
    onlyHeartRate: Boolean,
    onToggleFilter: () -> Unit,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onConnectSaved: () -> Unit,
    onDisconnect: () -> Unit,
) {
    if (!state.available) {
        Text("Bluetooth indisponible sur ce téléphone.", color = MaterialTheme.colorScheme.error)
        return
    }
    LinkCard(state.link, onDisconnect)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        if (state.scanning) {
            OutlinedButton(onClick = onStop) { Text("Arrêter") }
        } else {
            Button(onClick = onScan) { Text("Rechercher") }
        }
        FilterChip(selected = onlyHeartRate, onClick = onToggleFilter, label = { Text("Service FC uniquement") })
    }
    if (!state.enabled) Text("Active le Bluetooth du téléphone.", color = MaterialTheme.colorScheme.error)
    saved?.let {
        ListItem(
            headlineContent = { Text("Montre mémorisée : ${it.name ?: it.address}") },
            supportingContent = { Text(it.address) },
            trailingContent = { TextButton(onClick = onConnectSaved) { Text("Connecter") } },
        )
    }
    state.devices.forEach { device ->
        ListItem(
            headlineContent = { Text((if (device.advertisesHeartRate) "♥ " else "") + (device.name ?: "(sans nom)")) },
            supportingContent = {
                Text(listOfNotNull(device.address, device.rssi?.let { "$it dBm" }, "appairé".takeIf { device.bonded }).joinToString(" · "))
            },
            modifier = Modifier.clickable { onConnect(device) },
        )
    }
}

@Composable
private fun LinkCard(link: BleLink, onDisconnect: () -> Unit) {
    if (link == BleLink.Idle) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(link) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Card(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            when (link) {
                is BleLink.Connecting -> Text("Connexion à ${link.device.name ?: link.device.address}…")
                is BleLink.Connected -> {
                    Text(link.device.name ?: link.device.address, style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${link.last?.bpm ?: "--"}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        Text(" bpm", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    val age = link.lastAtMillis?.let { (now - it).coerceAtLeast(0) / 1000 }
                    Text(
                        "${link.notifications} mesures · dernière il y a ${age ?: "-"} s" +
                            (link.last?.sensorContact?.let { if (it) " · contact OK" else " · pas de contact" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is BleLink.Failed -> Text("Échec : ${link.reason}", color = MaterialTheme.colorScheme.error)
                is BleLink.Reconnecting -> Text("Reconnexion à ${link.device.name ?: link.device.address}…")
                BleLink.Idle -> Unit
            }
            if (link !is BleLink.Failed) {
                TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.End)) { Text("Déconnecter") }
            }
        }
    }
}

@Composable
private fun HealthConnectSection(
    ui: HealthConnectUi,
    permissions: Set<String>,
    onGrant: () -> Unit,
    onHours: (Int) -> Unit,
    onRead: () -> Unit,
) {
    when (ui.availability) {
        HealthConnectAvailability.UNAVAILABLE -> {
            Text("Health Connect indisponible sur ce téléphone.", color = MaterialTheme.colorScheme.error)
            return
        }
        HealthConnectAvailability.UPDATE_REQUIRED -> {
            Text("Health Connect doit être mis à jour (Play Store).", color = MaterialTheme.colorScheme.error)
            return
        }
        HealthConnectAvailability.AVAILABLE -> Unit
    }
    Text(
        "Dans Mi Fitness : Profil → Paramètres → Health Connect, active la fréquence cardiaque et les calories.",
        style = MaterialTheme.typography.bodySmall,
    )
    permissions.forEach { p ->
        Text("${if (p in ui.granted) "✓" else "✗"} ${PERMISSION_LABELS[p.substringAfterLast('.')] ?: p.substringAfterLast('.')}")
    }
    Text(
        "Lecture en arrière-plan : ${if (ui.backgroundSupported) "supportée" else "non supportée"}",
        style = MaterialTheme.typography.bodySmall,
    )
    if (!ui.granted.containsAll(permissions)) {
        Button(onClick = onGrant, modifier = Modifier.padding(top = 8.dp)) { Text("Autoriser l'accès") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        listOf(1, 3, 24).forEach { h ->
            FilterChip(selected = ui.hours == h, onClick = { onHours(h) }, label = { Text("$h h") })
        }
        OutlinedButton(onClick = onRead, enabled = !ui.reading) { Text(if (ui.reading) "Lecture…" else "Lire") }
    }
    ui.probe?.let {
        Text(
            WatchTestViewModel.probeText(it),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    ui.error?.let { Text("Erreur : $it", color = MaterialTheme.colorScheme.error) }
}

private val PERMISSION_LABELS = mapOf(
    "READ_HEART_RATE" to "Fréquence cardiaque",
    "READ_ACTIVE_CALORIES_BURNED" to "Calories actives",
    "READ_TOTAL_CALORIES_BURNED" to "Calories totales",
    "READ_HEALTH_DATA_IN_BACKGROUND" to "Lecture en arrière-plan",
)
