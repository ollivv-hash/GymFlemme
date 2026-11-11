package fr.olivier.forme.feature.sharing

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import fr.olivier.forme.R
import fr.olivier.forme.feature.nutrition.ui.CameraPreview
import fr.olivier.forme.ui.components.FormeTopBar

/** Scanner un modèle (V2.27): the camera reads QR codes; the first GymFlemme one opens its preview. */
@Composable
fun TemplateScanScreen(onBack: () -> Unit, onPayload: (String) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var other by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }

    Scaffold(topBar = { FormeTopBar(stringResource(R.string.scan_template), onBack) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (granted) {
                CameraPreview(
                    onBarcode = { value ->
                        if (done) return@CameraPreview
                        if (TemplateShareCodec.isQrPayload(value)) {
                            done = true
                            ContextCompat.getMainExecutor(context).execute { onPayload(value) }
                        } else {
                            other = true
                        }
                    },
                    onController = {},
                    formats = intArrayOf(Barcode.FORMAT_QR_CODE),
                )
            } else {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.scan_template_camera))
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.scan_template_allow)) }
                }
            }
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                Text(
                    stringResource(if (other) R.string.scan_template_other else R.string.scan_template_hint),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
