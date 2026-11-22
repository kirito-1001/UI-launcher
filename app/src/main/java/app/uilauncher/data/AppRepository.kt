package app.uilauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
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

    // The icon style (pack + shape) applied to every icon (home, dock, drawer). Changing it bumps
    // [iconVersion] so icons reload. [applied] is replaced as one object so a style and its pack never mix.
    private class Applied(val style: IconStyle, val pack: IconPack?)

    @Volatile
    private var applied = Applied(IconStyle(), null)

    @Volatile
    private var requested: IconStyle? = null

    private val _iconVersion = MutableStateFlow(0)
    val iconVersion: StateFlow<Int> = _iconVersion.asStateFlow()

    private val sampleCache = ConcurrentHashMap<String, List<ImageBitmap>>()
    private val customIcons = CustomIcons(context)

    @Volatile
    private var activities: Map<String, LauncherActivityInfo> = emptyMap()

    /** Called when a package was uninstalled, so the layout can drop its icons. */
    var removedListener: ((String) -> Unit)? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) {
            if (packageName != null) {
                evict(packageName)
                removedListener?.invoke(packageName)
                if (packageName == this@AppRepository.requested?.pack) reloadPack()
            }
            reload()
        }

        override fun onPackageAdded(packageName: String?, user: UserHandle?) {
            reload()
        }

        override fun onPackageChanged(packageName: String?, user: UserHandle?) {
            if (packageName != null) {
                evict(packageName)
                if (packageName == this@AppRepository.requested?.pack) reloadPack()
            }
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

    /**
     * Applies an icon style to all icons: the icon pack (empty = none) and the shape of the apps' own icons.
     * The pack is only read again when its package changed (or [reloadPack] forces it), so dragging the
     * corner slider does not parse the pack's appfilter over and over.
     */
    fun setIconStyle(style: IconStyle, forcePackReload: Boolean = false) {
        if (style == requested) return
        requested = style
        scope.launch {
            val previous = applied
            val pack = when {
                style.pack.isEmpty() -> null
                !forcePackReload && previous.style.pack == style.pack && previous.pack != null -> previous.pack
                else -> IconPack.load(context, style.pack)
            }
            if (requested != style) return@launch // a newer choice arrived while loading
            applied = Applied(style, pack)
            iconCache.clear()
            _iconVersion.value = _iconVersion.value + 1
        }
    }

    /** The pack was updated or removed: read it again (or fall back to the original icons). */
    private fun reloadPack() {
        val style = requested ?: return
        if (style.pack.isEmpty()) return
        sampleCache.remove(style.pack)
        requested = null
        setIconStyle(style, forcePackReload = true)
    }

    /** The icon pack currently applied, or null (used by the icon picker). */
    fun currentPack(): IconPack? = applied.pack

    fun hasCustomIcon(key: String): Boolean = customIcons.has(key)

    /** Gives one app its own icon, in every section. [shapeable]: follow the chosen icon shape. */
    suspend fun setCustomIcon(key: String, bitmap: Bitmap, shapeable: Boolean) {
        withContext(Dispatchers.IO) { customIcons.save(key, bitmap, shapeable) }
        iconCache.remove(key)
        _iconVersion.value = _iconVersion.value + 1
    }

    /** Back to the app's normal icon (or the pack's). */
    suspend fun clearCustomIcon(key: String) {
        withContext(Dispatchers.IO) { customIcons.remove(key) }
        iconCache.remove(key)
        _iconVersion.value = _iconVersion.value + 1
    }

    /** A few icons of [packageName], to preview the pack in the settings. */
    suspend fun packSample(packageName: String): List<ImageBitmap> = withContext(Dispatchers.Default) {
        sampleCache[packageName]?.let { return@withContext it }
        val sample = runCatching {
            IconPack.load(context, packageName)?.sample(4, SAMPLE_PX)?.map { it.asImageBitmap() }
        }.getOrNull() ?: emptyList()
        sampleCache[packageName] = sample
        sample
    }

    suspend fun loadIcon(key: String): ImageBitmap? = withContext(Dispatchers.Default) {
        val cached = iconCache[key]
        if (cached != null) return@withContext cached
        val info = activities[key] ?: return@withContext null
        val a = applied
        val bitmap = runCatching {
            val component = info.componentName
            val custom = customIcons.load(key)
            val result = if (custom != null) {
                // The icon the user picked for this app wins over everything else.
                if (custom.shapeable) IconShapes.clip(custom.bitmap, a.style, ICON_PX) else custom.bitmap
            } else {
                // Pack icon first; otherwise the app's own icon, themed by the pack or cut to the chosen shape.
                a.pack?.exact(component.packageName, component.className, ICON_PX) ?: run {
                    val original = info.getIcon(0)
                    a.pack?.wrap(original, key, ICON_PX) ?: IconShapes.apply(original, a.style, ICON_PX)
                }
            }
            result.asImageBitmap()
        }.getOrNull() ?: return@withContext null
        // Do not cache an icon made with a style that was replaced in the meantime.
        if (applied === a) iconCache[key] = bitmap
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
        const val SAMPLE_PX = 96
    }
}
