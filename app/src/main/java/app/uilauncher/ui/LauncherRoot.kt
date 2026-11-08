package app.uilauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import app.uilauncher.LauncherViewModel

@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val settings by vm.settings.collectAsState()
    CompositionLocalProvider(LocalAppRepository provides vm.repo) {
        LauncherTheme(settings) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(vm)
                DrawerOverlay(vm)
                SettingsOverlay(vm)
            }
        }
    }
}
