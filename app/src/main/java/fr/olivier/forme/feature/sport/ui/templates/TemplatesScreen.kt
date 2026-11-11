package fr.olivier.forme.feature.sport.ui.templates

import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import kotlinx.coroutines.flow.asStateFlow
import fr.olivier.forme.feature.sharing.TemplateShareCodec
import fr.olivier.forme.feature.sharing.TemplateImport
import fr.olivier.forme.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.sport.data.TemplateRepository
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: TemplateRepository,
    private val nutrition: fr.olivier.forme.feature.nutrition.data.NutritionRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    val templates = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setArchived(id: Long, archived: Boolean) = viewModelScope.launch { repository.setArchived(id, archived) }

    /** V2.27: the template being shared, its first name offer, and both forms of the file. */
    private val _sharing = kotlinx.coroutines.flow.MutableStateFlow<ShareState?>(null)
    val sharing = _sharing.asStateFlow()

    fun share(id: Long) = viewModelScope.launch {
        val template = repository.get(id) ?: return@launch
        _sharing.value = ShareState(template, nutrition.currentProfile().firstName?.trim()?.takeIf { it.isNotEmpty() }, withName = false)
        rebuild()
    }

    fun setWithName(withName: Boolean) = viewModelScope.launch {
        _sharing.value = _sharing.value?.copy(withName = withName)
        rebuild()
    }

    private suspend fun rebuild() {
        val s = _sharing.value ?: return
        val file = TemplateImport.export(s.template, repository.getLines(s.template.id), s.firstName.takeIf { s.withName }, fr.olivier.forme.BuildConfig.VERSION_NAME)
        val payload = TemplateShareCodec.toQrPayload(file)
        _sharing.value = s.copy(json = TemplateShareCodec.encode(file), qrPayload = payload.takeIf(TemplateShareCodec::fitsInQr))
    }

    fun dismissShare() {
        _sharing.value = null
    }

    /** The file to send, written in the cache shared through the FileProvider. */
    fun shareFile(): android.net.Uri? {
        val s = _sharing.value ?: return null
        val json = s.json ?: return null
        val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
        val file = java.io.File(dir, TemplateImport.fileName(s.template.name)).apply { writeText(json) }
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }
}

/** [qrPayload] null: too long for a readable QR code. */
data class ShareState(
    val template: WorkoutTemplateEntity,
    val firstName: String?,
    val withName: Boolean,
    val json: String? = null,
    val qrPayload: String? = null,
)

const val TEMPLATE_MIME = "application/vnd.gymflemme.template+json"

fun WorkoutTemplateEntity.title() = if (code.isBlank()) name else "$code · $name"

@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onCreate: () -> Unit,
    onRotation: () -> Unit,
    onImport: () -> Unit = {},
    onScan: () -> Unit = {},
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val sharing by viewModel.sharing.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { FormeTopBar("Modèles de séance", onBack) { TextButton(onClick = onRotation) { Text("Rotation") } } },
        floatingActionButton = {
            ExtendedFloatingActionButton(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary, onClick = onCreate, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Modèle") })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            // V2.27: a template received from someone else.
            item(key = "import") {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = onImport) { Text(stringResource(R.string.import_template)) }
                    TextButton(onClick = onScan) { Text(stringResource(R.string.scan_template)) }
                }
            }
            templates.groupBy { it.archived }.toSortedMap().forEach { (archived, list) ->
                item(key = "h-$archived") {
                    SectionTitle(if (archived) "Archivés" else "Actifs", Modifier.padding(horizontal = 16.dp))
                }
                items(list, key = { it.id }) { template ->
                    ListItem(
                        modifier = Modifier.clickable { onEdit(template.id) },
                        headlineContent = { Text(template.title()) },
                        supportingContent = {
                            Text(template.orderInRotation?.let { "Rotation : position $it" } ?: "Hors rotation")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.share(template.id) }) {
                                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_template, template.name))
                                }
                                IconButton(onClick = { onDuplicate(template.id) }) {
                                    Icon(painterResource(R.drawable.ic_copy), contentDescription = "Dupliquer")
                                }
                                IconButton(onClick = { viewModel.setArchived(template.id, !template.archived) }) {
                                    Icon(
                                        if (template.archived) Icons.Filled.Refresh else Icons.Filled.Delete,
                                        contentDescription = if (template.archived) "Désarchiver" else "Archiver",
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    sharing?.let { ShareDialog(it, viewModel) }
}

/** Partager (V2.27): the first name if wanted, then a file through the share sheet, or a QR code. */
@Composable
private fun ShareDialog(state: ShareState, viewModel: TemplatesViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showQr by remember { mutableStateOf(false) }
    val chooserTitle = stringResource(R.string.share_file)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = viewModel::dismissShare,
        title = { Text(stringResource(R.string.share_title, state.template.name)) },
        text = {
            Column {
                androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(
                        checked = state.withName,
                        onCheckedChange = viewModel::setWithName,
                        enabled = state.firstName != null,
                    )
                    Text(
                        if (state.firstName != null) stringResource(R.string.share_with_name, state.firstName)
                        else stringResource(R.string.share_no_name),
                    )
                }
                Text(stringResource(R.string.share_what), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showQr && state.qrPayload != null) {
                    TemplateQrCode(state.qrPayload, Modifier.padding(top = 12.dp).fillMaxWidth())
                }
                if (state.json != null && state.qrPayload == null) {
                    Text(stringResource(R.string.share_qr_too_long), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = state.json != null, onClick = {
                viewModel.shareFile()?.let { uri ->
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = TEMPLATE_MIME
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Modèle GymFlemme : ${state.template.name}")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, chooserTitle))
                }
            }) { Text(stringResource(R.string.share_file)) }
        },
        dismissButton = {
            TextButton(enabled = state.qrPayload != null && !showQr, onClick = { showQr = true }) { Text(stringResource(R.string.share_qr)) }
        },
    )
}

const val TEMPLATE_QR_TAG = "template_qr"

/** The QR code of a shared template, drawn by ZXing (level M, quiet zone of 2 modules). */
@Composable
fun TemplateQrCode(payload: String, modifier: Modifier = Modifier) {
    val bitmap = remember(payload) {
        val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
            payload, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0,
            mapOf(
                com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
                com.google.zxing.EncodeHintType.MARGIN to 2,
                com.google.zxing.EncodeHintType.CHARACTER_SET to "ISO-8859-1",
            ),
        )
        val bmp = android.graphics.Bitmap.createBitmap(matrix.width, matrix.height, android.graphics.Bitmap.Config.ARGB_8888)
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            bmp.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
        bmp.asImageBitmap()
    }
    androidx.compose.foundation.Image(
        bitmap = bitmap,
        contentDescription = stringResource(R.string.share_qr_description),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
        modifier = modifier.aspectRatio(1f).testTag(TEMPLATE_QR_TAG),
    )
}

