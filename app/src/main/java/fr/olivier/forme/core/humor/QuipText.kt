package fr.olivier.forme.core.humor

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf

/** Provided by MainActivity; null (no quips shown) in screens composed on their own. */
val LocalQuips = staticCompositionLocalOf<QuipRepository?> { null }

/**
 * The text of [quip], drawn once when the screen opens and kept across recompositions, rotation and back navigation
 * (V2.15). A new [key] draws again (e.g. one draw per logged set). Null until drawn.
 */
@Composable
fun rememberQuip(quip: Quip, vararg args: Any, key: Any? = null): String? {
    val repository = LocalQuips.current
    var index by rememberSaveable(quip, key) { mutableStateOf<Int?>(null) }
    val styleFlow = remember(repository) { repository?.style ?: flowOf(null) }
    val style by styleFlow.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(repository, quip, key) {
        if (index == null && repository != null) index = repository.draw(quip)
    }
    val resources = LocalContext.current.resources
    val drawn = index ?: return null
    return Quips.text(resources, quip, drawn, style ?: return null, *args)
}

/** [rememberQuip] as a Text, once drawn. */
@Composable
fun QuipText(
    quip: Quip,
    vararg args: Any,
    key: Any? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    rememberQuip(quip, *args, key = key)?.let { Text(it, modifier, color = color, style = style) }
}
