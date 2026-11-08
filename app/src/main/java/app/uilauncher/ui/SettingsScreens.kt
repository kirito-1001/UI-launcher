package app.uilauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.uilauncher.BuildConfig
import app.uilauncher.LauncherViewModel
import app.uilauncher.R
import app.uilauncher.SettingsScreen
import app.uilauncher.data.Keys

@Composable
fun SettingsOverlay(vm: LauncherViewModel) {
    BackHandler(enabled = vm.screen != SettingsScreen.None) { vm.settingsBack() }

    // Remember the last real screen so it stays visible while the exit animation runs.
    var last by remember { mutableStateOf(SettingsScreen.Main) }
    val shown = if (vm.screen != SettingsScreen.None) vm.screen else last
    SideEffect { if (vm.screen != SettingsScreen.None) last = vm.screen }

    AnimatedVisibility(
        visible = vm.screen != SettingsScreen.None,
        enter = fadeIn() + slideInHorizontally { it / 6 },
        exit = fadeOut(),
    ) {
        when (shown) {
            SettingsScreen.Home -> HomeSettings(vm)
            SettingsScreen.Drawer -> DrawerSettings(vm)
            SettingsScreen.Dock -> DockSettings(vm)
            SettingsScreen.Appearance -> AppearanceSettings(vm)
            SettingsScreen.HiddenApps -> HiddenAppsSettings(vm)
            SettingsScreen.About -> AboutSettings(vm)
            else -> MainSettings(vm)
        }
    }
}

@Composable
private fun MainSettings(vm: LauncherViewModel) {
    val context = LocalContext.current
    SettingsPage(stringResource(R.string.settings), onBack = vm::settingsBack) {
        NavRow(
            title = stringResource(R.string.default_launcher),
            summary = stringResource(R.string.default_launcher_summary),
            onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
        )
        NavRow(stringResource(R.string.settings_home), stringResource(R.string.summary_home)) { vm.openSettingsScreen(SettingsScreen.Home) }
        NavRow(stringResource(R.string.settings_drawer), stringResource(R.string.summary_drawer)) { vm.openSettingsScreen(SettingsScreen.Drawer) }
        NavRow(stringResource(R.string.settings_dock), stringResource(R.string.summary_dock)) { vm.openSettingsScreen(SettingsScreen.Dock) }
        NavRow(stringResource(R.string.settings_appearance), stringResource(R.string.summary_appearance)) { vm.openSettingsScreen(SettingsScreen.Appearance) }
        NavRow(stringResource(R.string.settings_about), stringResource(R.string.summary_about)) { vm.openSettingsScreen(SettingsScreen.About) }
    }
}

@Composable
private fun HomeSettings(vm: LauncherViewModel) {
    val s by vm.settings.collectAsState()
    SettingsPage(stringResource(R.string.settings_home), onBack = vm::settingsBack) {
        SectionHeader(stringResource(R.string.section_grid))
        SliderRow(stringResource(R.string.columns), s.homeCols, 3..8) { vm.set(Keys.homeCols, it) }
        SliderRow(stringResource(R.string.rows), s.homeRows, 3..9) { vm.set(Keys.homeRows, it) }
        SliderRow(stringResource(R.string.spacing_horizontal), s.homePadH, 0..48, "dp") { vm.set(Keys.homePadH, it) }
        SliderRow(stringResource(R.string.spacing_vertical), s.homePadV, 0..48, "dp") { vm.set(Keys.homePadV, it) }

        SectionHeader(stringResource(R.string.section_icons))
        SliderRow(stringResource(R.string.icon_size), s.homeIconSize, 32..96, "dp") { vm.set(Keys.homeIconSize, it) }
        SwitchRow(stringResource(R.string.show_labels), s.homeShowLabels) { vm.set(Keys.homeShowLabels, it) }
        SliderRow(stringResource(R.string.label_size), s.homeLabelSize, 8..18, "sp") { vm.set(Keys.homeLabelSize, it) }

        SectionHeader(stringResource(R.string.section_search))
        SwitchRow(stringResource(R.string.show_search_bar), s.showSearchBar) { vm.set(Keys.showSearchBar, it) }
    }
}

@Composable
private fun DrawerSettings(vm: LauncherViewModel) {
    val s by vm.settings.collectAsState()
    SettingsPage(stringResource(R.string.settings_drawer), onBack = vm::settingsBack) {
        SectionHeader(stringResource(R.string.section_grid))
        SliderRow(stringResource(R.string.columns), s.drawerCols, 3..8) { vm.set(Keys.drawerCols, it) }
        ChoiceRow(
            title = stringResource(R.string.drawer_scroll),
            options = listOf(stringResource(R.string.scroll_vertical), stringResource(R.string.scroll_horizontal)),
            selected = if (s.drawerHorizontal) 1 else 0,
        ) { vm.set(Keys.drawerHorizontal, it == 1) }
        if (s.drawerHorizontal) {
            SliderRow(stringResource(R.string.drawer_rows), s.drawerRows, 3..9) { vm.set(Keys.drawerRows, it) }
        }

        SectionHeader(stringResource(R.string.section_icons))
        SliderRow(stringResource(R.string.icon_size), s.drawerIconSize, 32..96, "dp") { vm.set(Keys.drawerIconSize, it) }
        SwitchRow(stringResource(R.string.show_labels), s.drawerShowLabels) { vm.set(Keys.drawerShowLabels, it) }
        SliderRow(stringResource(R.string.label_size), s.drawerLabelSize, 8..18, "sp") { vm.set(Keys.drawerLabelSize, it) }

        SectionHeader(stringResource(R.string.section_background))
        SliderRow(stringResource(R.string.background_opacity), s.drawerBgOpacity, 0..100, "%") { vm.set(Keys.drawerBgOpacity, it) }

        SectionHeader(stringResource(R.string.section_apps))
        NavRow(
            title = stringResource(R.string.hidden_apps),
            summary = stringResource(R.string.hidden_apps_summary),
        ) { vm.openSettingsScreen(SettingsScreen.HiddenApps) }
    }
}

@Composable
private fun DockSettings(vm: LauncherViewModel) {
    val s by vm.settings.collectAsState()
    SettingsPage(stringResource(R.string.settings_dock), onBack = vm::settingsBack) {
        SectionHeader(stringResource(R.string.section_icons))
        SliderRow(stringResource(R.string.dock_icons), s.dockCount, 3..7) { vm.set(Keys.dockCount, it) }
        SliderRow(stringResource(R.string.icon_size), s.dockIconSize, 32..96, "dp") { vm.set(Keys.dockIconSize, it) }
        SwitchRow(stringResource(R.string.show_labels), s.dockShowLabels) { vm.set(Keys.dockShowLabels, it) }

        SectionHeader(stringResource(R.string.section_background))
        SliderRow(stringResource(R.string.background_opacity), s.dockBgOpacity, 0..100, "%") { vm.set(Keys.dockBgOpacity, it) }
    }
}

@Composable
private fun AppearanceSettings(vm: LauncherViewModel) {
    val s by vm.settings.collectAsState()
    SettingsPage(stringResource(R.string.settings_appearance), onBack = vm::settingsBack) {
        SectionHeader(stringResource(R.string.section_theme))
        ChoiceRow(
            title = stringResource(R.string.theme),
            options = listOf(
                stringResource(R.string.theme_system),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark),
            ),
            selected = s.themeMode,
        ) { vm.set(Keys.themeMode, it) }
        SwitchRow(
            title = stringResource(R.string.dynamic_color),
            summary = stringResource(R.string.dynamic_color_summary),
            checked = s.dynamicColor,
        ) { vm.set(Keys.dynamicColor, it) }
    }
}

@Composable
private fun HiddenAppsSettings(vm: LauncherViewModel) {
    val s by vm.settings.collectAsState()
    val apps by vm.repo.apps.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            SettingsHeader(stringResource(R.string.hidden_apps), vm::settingsBack)
            LazyColumn(Modifier.weight(1f)) {
                items(apps, key = { it.key }) { app ->
                    val hidden = app.key in s.hiddenApps
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.toggleHidden(app.key) }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(app, 40.dp)
                        Text(app.label, modifier = Modifier.weight(1f).padding(start = 16.dp))
                        Checkbox(checked = hidden, onCheckedChange = { vm.toggleHidden(app.key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSettings(vm: LauncherViewModel) {
    SettingsPage(stringResource(R.string.settings_about), onBack = vm::settingsBack) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
