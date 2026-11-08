package app.uilauncher.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "ui_launcher")

/**
 * All preference keys. To add a new option: add a key here, a field in [LauncherSettings]
 * (with its default), read it in [toSettings], then add one row in the settings screen.
 */
object Keys {
    val layout = stringPreferencesKey("layout")

    // Home screen
    val homeCols = intPreferencesKey("home_cols")
    val homeRows = intPreferencesKey("home_rows")
    val homePadH = intPreferencesKey("home_pad_h")
    val homePadV = intPreferencesKey("home_pad_v")
    val homeIconSize = intPreferencesKey("home_icon_size")
    val homeShowLabels = booleanPreferencesKey("home_show_labels")
    val homeLabelSize = intPreferencesKey("home_label_size")
    val showSearchBar = booleanPreferencesKey("show_search_bar")

    // App drawer
    val drawerCols = intPreferencesKey("drawer_cols")
    val drawerRows = intPreferencesKey("drawer_rows")
    val drawerHorizontal = booleanPreferencesKey("drawer_horizontal")
    val drawerIconSize = intPreferencesKey("drawer_icon_size")
    val drawerShowLabels = booleanPreferencesKey("drawer_show_labels")
    val drawerLabelSize = intPreferencesKey("drawer_label_size")
    val drawerBgOpacity = intPreferencesKey("drawer_bg_opacity")
    val hiddenApps = stringSetPreferencesKey("hidden_apps")

    // Dock
    val dockCount = intPreferencesKey("dock_count")
    val dockIconSize = intPreferencesKey("dock_icon_size")
    val dockShowLabels = booleanPreferencesKey("dock_show_labels")
    val dockBgOpacity = intPreferencesKey("dock_bg_opacity")

    // Appearance
    val themeMode = intPreferencesKey("theme_mode")
    val dynamicColor = booleanPreferencesKey("dynamic_color")
}

data class LauncherSettings(
    // Home screen
    val homeCols: Int = 4,
    val homeRows: Int = 5,
    val homePadH: Int = 8,
    val homePadV: Int = 8,
    val homeIconSize: Int = 56,
    val homeShowLabels: Boolean = true,
    val homeLabelSize: Int = 12,
    val showSearchBar: Boolean = true,
    // App drawer
    val drawerCols: Int = 4,
    val drawerRows: Int = 5,
    val drawerHorizontal: Boolean = false,
    val drawerIconSize: Int = 52,
    val drawerShowLabels: Boolean = true,
    val drawerLabelSize: Int = 12,
    val drawerBgOpacity: Int = 92,
    val hiddenApps: Set<String> = emptySet(),
    // Dock
    val dockCount: Int = 5,
    val dockIconSize: Int = 52,
    val dockShowLabels: Boolean = false,
    val dockBgOpacity: Int = 0,
    // Appearance: 0 = system, 1 = light, 2 = dark
    val themeMode: Int = 0,
    val dynamicColor: Boolean = true,
)

fun Preferences.toSettings(): LauncherSettings {
    val d = LauncherSettings()
    return LauncherSettings(
        homeCols = this[Keys.homeCols] ?: d.homeCols,
        homeRows = this[Keys.homeRows] ?: d.homeRows,
        homePadH = this[Keys.homePadH] ?: d.homePadH,
        homePadV = this[Keys.homePadV] ?: d.homePadV,
        homeIconSize = this[Keys.homeIconSize] ?: d.homeIconSize,
        homeShowLabels = this[Keys.homeShowLabels] ?: d.homeShowLabels,
        homeLabelSize = this[Keys.homeLabelSize] ?: d.homeLabelSize,
        showSearchBar = this[Keys.showSearchBar] ?: d.showSearchBar,
        drawerCols = this[Keys.drawerCols] ?: d.drawerCols,
        drawerRows = this[Keys.drawerRows] ?: d.drawerRows,
        drawerHorizontal = this[Keys.drawerHorizontal] ?: d.drawerHorizontal,
        drawerIconSize = this[Keys.drawerIconSize] ?: d.drawerIconSize,
        drawerShowLabels = this[Keys.drawerShowLabels] ?: d.drawerShowLabels,
        drawerLabelSize = this[Keys.drawerLabelSize] ?: d.drawerLabelSize,
        drawerBgOpacity = this[Keys.drawerBgOpacity] ?: d.drawerBgOpacity,
        hiddenApps = this[Keys.hiddenApps] ?: d.hiddenApps,
        dockCount = this[Keys.dockCount] ?: d.dockCount,
        dockIconSize = this[Keys.dockIconSize] ?: d.dockIconSize,
        dockShowLabels = this[Keys.dockShowLabels] ?: d.dockShowLabels,
        dockBgOpacity = this[Keys.dockBgOpacity] ?: d.dockBgOpacity,
        themeMode = this[Keys.themeMode] ?: d.themeMode,
        dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
    )
}
