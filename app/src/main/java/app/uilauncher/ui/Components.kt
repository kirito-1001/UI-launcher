package app.uilauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import app.uilauncher.data.AppInfo
import app.uilauncher.data.AppRepository

val LocalAppRepository = compositionLocalOf<AppRepository> { error("AppRepository not provided") }

/** The app icon, loaded in the background and cached by the repository. */
@Composable
fun AppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier) {
    val repo = LocalAppRepository.current
    // Reload when the icon pack changes. The state is reset only when this slot shows a different app;
    // on a pack change the old icon stays until the new one is ready (no blank flash).
    val version by repo.iconVersion.collectAsState()
    var bitmap by remember(app.key) { mutableStateOf(repo.cachedIcon(app.key)) }
    LaunchedEffect(app.key, version) {
        bitmap = repo.cachedIcon(app.key) ?: repo.loadIcon(app.key) ?: bitmap
    }
    Box(modifier.size(size)) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size))
        }
    }
}

/** Icon with an optional name below it. Used by the home screen, dock and drawer. */
@Composable
fun AppItem(
    app: AppInfo,
    iconSize: Int,
    showLabel: Boolean,
    labelSize: Int,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    shadow: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(app, iconSize.dp)
        if (showLabel) {
            Text(
                text = app.label,
                color = textColor,
                fontSize = labelSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = if (shadow) TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f)) else TextStyle.Default,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
