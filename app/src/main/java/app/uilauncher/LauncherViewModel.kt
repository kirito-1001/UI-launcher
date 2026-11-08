package app.uilauncher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.uilauncher.data.AppInfo
import app.uilauncher.data.AppRepository
import app.uilauncher.data.HomeLayout
import app.uilauncher.data.Keys
import app.uilauncher.data.LauncherSettings
import app.uilauncher.data.dataStore
import app.uilauncher.data.toSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SettingsScreen { None, Main, Home, Drawer, Dock, Appearance, HiddenApps, About }

/** An app dropped after being dragged out of the drawer. [pos] is in window coordinates. */
data class DropEvent(val app: AppInfo, val pos: Offset)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx = application.applicationContext
    val repo = AppRepository(ctx)

    val settings: StateFlow<LauncherSettings> = ctx.dataStore.data
        .map { it.toSettings() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    private val _layout = MutableStateFlow(HomeLayout())
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()
    private val layoutLoaded = CompletableDeferred<Unit>()

    val appMap: StateFlow<Map<String, AppInfo>> = repo.apps
        .map { list -> list.associateBy { it.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ---- UI state ----
    var drawerOpen by mutableStateOf(false)
        private set
    var drawerFocusSearch by mutableStateOf(false)
        private set
    var screen by mutableStateOf(SettingsScreen.None)
        private set

    /** The home page currently shown (set by the home screen). */
    var homePage by mutableIntStateOf(0)

    private val _homeEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homeEvents: SharedFlow<Unit> = _homeEvents.asSharedFlow()

    // ---- Dragging an app out of the drawer onto the home screen / dock ----

    /** The app being dragged out of the drawer, or null. */
    var dragApp by mutableStateOf<AppInfo?>(null)
        private set

    /** Finger position of that drag, in window coordinates. */
    var dragPos by mutableStateOf(Offset.Zero)
        private set

    /**
     * True from the moment a drag leaves the drawer until the drawer is opened again. The drawer stays
     * composed (so it keeps receiving the finger) but is invisible, which reveals the home screen.
     */
    var drawerHiddenForDrag by mutableStateOf(false)
        private set

    private val _dropEvents = MutableSharedFlow<DropEvent>(extraBufferCapacity = 4)
    val dropEvents: SharedFlow<DropEvent> = _dropEvents.asSharedFlow()

    init {
        repo.removedListener = { pkg -> updateLayout { it.removePackage(pkg) } }
        repo.start()

        viewModelScope.launch {
            val prefs = ctx.dataStore.data.first()
            _layout.value = HomeLayout.fromJson(prefs[Keys.layout])
            layoutLoaded.complete(Unit)
            if (!_layout.value.initialized) {
                val list = repo.apps.first { it.isNotEmpty() }
                val dock = defaultDock(list)
                updateLayout { it.copy(initialized = true, dock = if (it.dock.isEmpty()) dock else it.dock) }
            }
        }

        // When the home grid gets smaller, icons that no longer fit are moved to free cells.
        viewModelScope.launch {
            layoutLoaded.await()
            val def = LauncherSettings()
            ctx.dataStore.data
                .map { (it[Keys.homeCols] ?: def.homeCols) to (it[Keys.homeRows] ?: def.homeRows) }
                .distinctUntilChanged()
                .collect { (cols, rows) -> updateLayout { it.fit(cols, rows) } }
        }
    }

    override fun onCleared() {
        repo.stop()
        super.onCleared()
    }

    // ---- Layout ----

    private fun updateLayout(block: (HomeLayout) -> HomeLayout) {
        val old = _layout.value
        val new = block(old)
        if (new == old) return
        _layout.value = new
        viewModelScope.launch { ctx.dataStore.edit { it[Keys.layout] = new.toJson() } }
    }

    private fun defaultDock(list: List<AppInfo>): List<String> {
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        val keys = ArrayList<String>()
        for (intent in intents) {
            val pkg = runCatching {
                pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            }.getOrNull() ?: continue
            val app = list.firstOrNull { it.packageName == pkg } ?: continue
            if (app.key !in keys) keys.add(app.key)
        }
        return keys
    }

    fun moveItem(id: String, page: Int, row: Int, col: Int) = updateLayout { it.move(id, page, row, col) }

    fun removeItem(id: String) = updateLayout { it.remove(id) }

    fun addPage() = updateLayout { it.addPage() }

    fun removePage(page: Int) {
        val current = _layout.value
        when {
            current.pages <= 1 -> toast(R.string.remove_page_last)
            current.hasItemsOn(page) -> toast(R.string.remove_page_not_empty)
            else -> updateLayout { it.removePage(page) }
        }
    }

    fun addToHome(app: AppInfo) {
        val s = settings.value
        val result = _layout.value.add(app.key, homePage, s.homeCols, s.homeRows)
        if (result == null) {
            toast(R.string.max_pages)
        } else {
            updateLayout { result }
            toast(R.string.added_to_home)
        }
    }

    fun startDrag(app: AppInfo, pos: Offset) {
        dragApp = app
        dragPos = pos
        drawerHiddenForDrag = true
    }

    fun dragTo(pos: Offset) {
        dragPos = pos
    }

    /** Finger lifted: tell the home screen and dock where, then go back to the home screen. */
    fun endDrag() {
        val app = dragApp ?: return
        _dropEvents.tryEmit(DropEvent(app, dragPos))
        dragApp = null
        drawerOpen = false
    }

    /** The drag was interrupted (for example the icon left the screen): drop nothing. */
    fun cancelDrag() {
        dragApp = null
        drawerHiddenForDrag = false
    }

    /** Places [app] in the exact cell the user dropped it on. */
    fun addToHomeAt(app: AppInfo, page: Int, row: Int, col: Int) {
        val current = _layout.value
        if (!current.isFree(page, row, col)) {
            toast(R.string.no_space)
            return
        }
        updateLayout { it.addAt(app.key, page, row, col) }
    }

    fun showNoSpace() = toast(R.string.no_space)

    /** Puts [app] in the dock at the slot the user dropped it on. */
    fun addToDockAt(app: AppInfo, slot: Int) {
        val current = _layout.value
        if (current.dock.contains(app.key)) {
            toast(R.string.already_in_dock)
            return
        }
        val result = current.insertInDock(app.key, slot, settings.value.dockCount)
        if (result == null) toast(R.string.dock_full) else updateLayout { result }
    }

    fun addToDock(app: AppInfo) {
        val current = _layout.value
        if (current.dock.contains(app.key)) {
            toast(R.string.already_in_dock)
            return
        }
        val result = current.addToDock(app.key, settings.value.dockCount)
        if (result == null) {
            toast(R.string.dock_full)
        } else {
            updateLayout { result }
            toast(R.string.added_to_dock)
        }
    }

    fun removeFromDock(key: String) = updateLayout { it.removeFromDock(key) }

    // ---- Apps ----

    fun launch(app: AppInfo) {
        repo.launch(app)
        drawerOpen = false
    }

    fun openAppInfo(app: AppInfo) = repo.openAppInfo(app)

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }

    fun hideApp(app: AppInfo) {
        toggleHidden(app.key)
        toast(R.string.app_hidden)
    }

    fun toggleHidden(key: String) {
        val current = settings.value.hiddenApps
        val updated = if (key in current) current - key else current + key
        set(Keys.hiddenApps, updated)
    }

    // ---- Navigation ----

    fun openDrawer(focusSearch: Boolean = false) {
        drawerFocusSearch = focusSearch
        drawerHiddenForDrag = false
        drawerOpen = true
    }

    fun closeDrawer() {
        drawerOpen = false
    }

    fun openSettings() {
        drawerOpen = false
        screen = SettingsScreen.Main
    }

    fun openSettingsScreen(target: SettingsScreen) {
        screen = target
    }

    fun settingsBack() {
        screen = when (screen) {
            SettingsScreen.None, SettingsScreen.Main -> SettingsScreen.None
            SettingsScreen.HiddenApps -> SettingsScreen.Drawer
            else -> SettingsScreen.Main
        }
    }

    /** Home button pressed while the launcher is already on screen. */
    fun onHomePressed() {
        when {
            screen != SettingsScreen.None -> screen = SettingsScreen.None
            drawerOpen -> drawerOpen = false
            else -> _homeEvents.tryEmit(Unit)
        }
    }

    // ---- Settings ----

    fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { ctx.dataStore.edit { it[key] = value } }
    }

    private fun toast(resId: Int) {
        Toast.makeText(ctx, resId, Toast.LENGTH_SHORT).show()
    }
}
