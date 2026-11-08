package app.uilauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.uilauncher.LauncherViewModel
import kotlin.math.roundToInt

/** The icon dragged out of the drawer, drawn above everything and centered on the finger. */
@Composable
fun DragOverlay(vm: LauncherViewModel) {
    val app = vm.dragApp ?: return
    val settings by vm.settings.collectAsState()
    val boxSize = 84.dp
    val half = with(LocalDensity.current) { boxSize.toPx() / 2f }
    val pos = vm.dragPos
    Box(
        Modifier
            .offset { IntOffset((pos.x - half).roundToInt(), (pos.y - half).roundToInt()) }
            .size(boxSize)
            .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = 0.92f },
        contentAlignment = Alignment.Center,
    ) {
        AppItem(app, settings.homeIconSize, settings.homeShowLabels, settings.homeLabelSize)
    }
}
