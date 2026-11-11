package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.settings.ThemeMode
import fr.olivier.forme.feature.heart.data.LiveHeart
import fr.olivier.forme.feature.heart.data.LiveHeartStatus
import fr.olivier.forme.feature.heart.ui.LiveHeartStrip
import fr.olivier.forme.feature.heart.ui.RecoveryLine
import fr.olivier.forme.ui.theme.FormeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Live heart rate strip states and the rest recovery line. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LiveHeartViewsSmokeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun states() {
        val now = System.currentTimeMillis()
        rule.setContent {
            FormeTheme(fr.olivier.forme.core.settings.Appearance(mode = ThemeMode.DARK)) {
                Surface {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                        LiveHeartStrip(LiveHeart(LiveHeartStatus.CONNECTED, bpm = 142, atMillis = now, hrMax = 176), {}, {})
                        LiveHeartStrip(LiveHeart(LiveHeartStatus.CONNECTED, bpm = 98, atMillis = now, hrMax = 176), {}, {})
                        LiveHeartStrip(LiveHeart(LiveHeartStatus.WAITING, hrMax = 176), {}, {})
                        LiveHeartStrip(LiveHeart(LiveHeartStatus.RECONNECTING, hrMax = 176), {}, {})
                        LiveHeartStrip(LiveHeart(LiveHeartStatus.NO_PERMISSION), {}, {})
                        LiveHeartStrip(LiveHeart(LiveHeartStatus.NO_DEVICE), {}, {})
                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Column(Modifier.padding(16.dp)) {
                                RecoveryLine(LiveHeart(LiveHeartStatus.CONNECTED, bpm = 128, atMillis = now, hrMax = 176))
                                RecoveryLine(LiveHeart(LiveHeartStatus.CONNECTED, bpm = 101, atMillis = now, hrMax = 176))
                            }
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("142 bpm · zone 4").assertExists()
        rule.onNodeWithText("Active la diffusion FC sur la montre").assertExists()
        rule.onNodeWithText("♥ 128 bpm · récupéré sous 106").assertExists()
        rule.onNodeWithText("♥ 101 bpm · ✓ récupéré").assertExists()
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "h2-live-heart.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
