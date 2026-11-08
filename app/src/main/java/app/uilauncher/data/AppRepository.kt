package app.uilauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

/** Reads the installed apps through LauncherApps and keeps the list + icons up to date. */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val user: UserHandle = Process.myUserHandle()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

    @Volatile
    private var activities: Map<String, LauncherActivityInfo> = emptyMap()

    /** Called when a package was uninstalled, so the layout can drop its icons. */
    var removedListener: ((String) -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            if (packageName != null) {
                evict(packageName)
                removedListener?.invoke(packageName)
            }
            reload()
        }

        override fun onPackageAdded(packageName: String?, user: UserHandle?) {
            reload()
        }

        override fun onPackageChanged(packageName: String?, user: UserHandle?) {
            if (packageName != null) evict(packageName)
            reload()
        }

        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            reload()
        }

        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            reload()
        }
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
    }

    private fun evict(packageName: String) {
        val prefix = "$packageName/"
        iconCache.keys.removeIf { it.startsWith(prefix) }
    }

    fun reload() {
        scope.launch {
            val infos = runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
            val byKey = LinkedHashMap<String, LauncherActivityInfo>()
            val collator = Collator.getInstance()
            val list = ArrayList<AppInfo>()
            for (info in infos) {
                val pkg = info.componentName.packageName
                if (pkg == context.packageName) continue
                val cls = info.componentName.className
                val key = "$pkg/$cls"
                byKey[key] = info
                list.add(AppInfo(key, info.label?.toString() ?: pkg, pkg, cls))
            }
            list.sortWith(Comparator { a, b -> collator.compare(a.label, b.label) })
            activities = byKey
            _apps.value = list
        }
    }

    fun cachedIcon(key: String): ImageBitmap? = iconCache[key]

    suspend fun loadIcon(key: String): ImageBitmap? = withContext(Dispatchers.Default) {
        val cached = iconCache[key]
        if (cached != null) return@withContext cached
        val info = activities[key] ?: return@withContext null
        val bitmap = runCatching {
            info.getIcon(0).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        }.getOrNull() ?: return@withContext null
        iconCache[key] = bitmap
        bitmap
    }

    fun launch(app: AppInfo) {
        runCatching {
            launcherApps.startMainActivity(ComponentName(app.packageName, app.className), user, null, null)
        }
    }

    fun openAppInfo(app: AppInfo) {
        runCatching {
            launcherApps.startAppDetailsActivity(ComponentName(app.packageName, app.className), user, null, null)
        }
    }

    private companion object {
        const val ICON_PX = 192
    }
}
