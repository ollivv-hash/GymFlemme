package fr.olivier.forme.feature.nutrition.ui

import fr.olivier.forme.feature.badges.data.BadgeRepository
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.NutritionRepository.BarcodeResult
import fr.olivier.forme.feature.nutrition.data.per100
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.navigation.ScanRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.Executors
import javax.inject.Inject

sealed interface ScanState {
    data object Scanning : ScanState
    data class Looking(val barcode: String) : ScanState
    data class Found(val food: FoodEntity) : ScanState
    data class Problem(val barcode: String, val message: String, val suggestedName: String = "") : ScanState
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NutritionRepository,
    private val badges: BadgeRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ScanRoute>()
    val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val meal: MealType = MealType.valueOf(route.meal)
    private val _state = MutableStateFlow<ScanState>(ScanState.Scanning)
    val state = _state.asStateFlow()

    fun onBarcode(code: String) {
        if (_state.value !is ScanState.Scanning) return
        _state.value = ScanState.Looking(code)
        viewModelScope.launch {
            _state.value = when (val r = repository.lookupBarcode(code)) {
                is BarcodeResult.Known -> ScanState.Found(r.food)
                BarcodeResult.NotFound -> ScanState.Problem(code, "Produit inconnu d'Open Food Facts.")
                BarcodeResult.Offline -> ScanState.Problem(code, "Hors ligne : produit jamais scanné sur ce téléphone.")
                is BarcodeResult.Failed -> ScanState.Problem(code, "Open Food Facts indisponible (${r.message}).")
                is BarcodeResult.Incomplete -> ScanState.Problem(code, "« ${r.product.name} » n'a pas de valeur de protéines.", r.product.name)
            }
        }
    }

    fun rescan() {
        _state.value = ScanState.Scanning
    }

    fun add(food: FoodEntity, grams: Double, then: () -> Unit) = viewModelScope.launch {
        repository.addFood(food, date, meal, grams)
        runCatching { badges.evaluate() } // V2.18: Shaker d'or, announced at once
        then()
    }
}

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    onCreateFood: (barcode: String, name: String) -> Unit,
    onSearch: (LocalDate, MealType) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var torch by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<LifecycleCameraController?>(null) }

    Scaffold(
        topBar = {
            FormeTopBar("Scanner · ${viewModel.meal.label}", onBack) {
                TextButton(onClick = {
                    torch = !torch
                    controller?.enableTorch(torch)
                }) { Text(if (torch) "Lampe off" else "Lampe") }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (granted) {
                CameraPreview(onBarcode = viewModel::onBarcode, onController = { controller = it })
            } else {
                Column(Modifier.padding(16.dp)) {
                    Text("L'accès à la caméra est nécessaire pour scanner un code-barres.")
                    Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Autoriser la caméra") }
                }
            }
            Card(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    when (val s = state) {
                        ScanState.Scanning -> Text("Vise le code-barres du produit.")
                        is ScanState.Looking -> {
                            CircularProgressIndicator()
                            Text("Recherche du produit ${s.barcode}…")
                        }
                        is ScanState.Found -> Text(s.food.name, style = MaterialTheme.typography.titleMedium)
                        is ScanState.Problem -> {
                            Text(s.message)
                            Button(onClick = { onCreateFood(s.barcode, s.suggestedName) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Créer l'aliment (étiquette)")
                            }
                            OutlinedButton(onClick = { onSearch(viewModel.date, viewModel.meal) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Chercher dans Ciqual")
                            }
                            TextButton(onClick = viewModel::rescan) { Text("Scanner un autre produit") }
                        }
                    }
                }
            }
        }
    }

    (state as? ScanState.Found)?.let { found ->
        val food = found.food
        QuantityDialog(
            title = listOfNotNull(food.name, food.brand).joinToString(" · "),
            per100 = food.per100(),
            initialG = food.defaultPortionG ?: 100.0,
            portions = listOfNotNull(food.defaultPortionG, 50.0, 100.0, 150.0, 200.0),
            confirmLabel = "Ajouter",
            onConfirm = { grams -> viewModel.add(food, grams, onAdded) },
            onDismiss = viewModel::rescan,
        )
    }
}

/** CameraX preview with an ML Kit analyzer limited to product barcodes (EAN / UPC). */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onBarcode: (String) -> Unit, onController: (LifecycleCameraController) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            setImageAnalysisAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null) {
                    proxy.close()
                    return@setImageAnalysisAnalyzer
                }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onBarcode) }
                    .addOnCompleteListener { proxy.close() }
            }
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onController(controller)
        onDispose {
            controller.unbind()
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView(
        factory = { ctx -> PreviewView(ctx).also { it.controller = controller } },
        modifier = Modifier.fillMaxSize(),
    )
}
