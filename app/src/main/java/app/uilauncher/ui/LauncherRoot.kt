package app.uilauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.uilauncher.LauncherViewModel

@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val settings by vm.settings.collectAsState()
    val view = LocalView.current
    LaunchedEffect(settings.hideStatusBar, settings.hideNavBar) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (settings.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
        if (settings.hideNavBar) controller.hide(WindowInsetsCompat.Type.navigationBars()) else controller.show(WindowInsetsCompat.Type.navigationBars())
    }
    CompositionLocalProvider(LocalAppRepository provides vm.repo) {
        LauncherTheme(settings) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(vm)
                FolderOverlay(vm)
                DrawerOverlay(vm)
                SettingsOverlay(vm)
                IconEditor(vm)
                DragOverlay(vm)
            }
        }
    }
}
