package fr.olivier.forme.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.olivier.forme.ui.theme.BentoDims
import fr.olivier.forme.ui.theme.LocalBento

data class NavEntry(val label: String, val icon: Int, val selected: Boolean, val onClick: () -> Unit)

/**
 * Floating navigation bar (V2.22): a dark pill detached from the edges, icons only (each with its description), the
 * active tab a rounded square in the accent colour.
 */
@Composable
fun FloatingNavBar(entries: List<NavEntry>, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    Row(
        modifier
            .navigationBarsPadding()
            .padding(start = BentoDims.screenMargin, end = BentoDims.screenMargin, bottom = BentoDims.navBottom)
            .fillMaxWidth()
            .height(BentoDims.navHeight)
            .clip(RoundedCornerShape(BentoDims.navRadius))
            .background(c.nav),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            Box(
                Modifier
                    .size(BentoDims.touch)
                    .clip(RoundedCornerShape(BentoDims.squareRadius))
                    .background(if (entry.selected) c.accent else Color.Transparent)
                    .selectable(selected = entry.selected, role = Role.Tab, onClick = entry.onClick)
                    .semantics { contentDescription = entry.label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(entry.icon),
                    contentDescription = null,
                    tint = if (entry.selected) c.onAccent else c.navInactive,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
