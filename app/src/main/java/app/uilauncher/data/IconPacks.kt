package app.uilauncher.data

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import kotlin.math.abs

data class IconPackInfo(val packageName: String, val label: String)

object IconPacks {
    /** The intent actions that icon packs declare (ADW, Nova, Apex, GO and the common older ones). */
    val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.fede.launcher.THEME_ICONPACK",
        "com.gau.go.launcherex.theme",
    )

    /** Icon packs installed on the phone, sorted by name. */
    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, IconPackInfo>()
        for (action in ACTIONS) {
            val matches = runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList())
            for (match in matches) {
                val pkg = match.activityInfo.packageName
                if (pkg == context.packageName || found.containsKey(pkg)) continue
                val label = match.activityInfo.applicationInfo.loadLabel(pm).toString()
                found[pkg] = IconPackInfo(pkg, label)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }
}

/**
 * One icon pack, read from its `appfilter.xml` (either `res/xml/appfilter` or `assets/appfilter.xml`).
 * - `<item component="ComponentInfo{pkg/cls}" drawable="name"/>` maps an app to its icon.
 * - `<iconback>`, `<iconmask>`, `<iconupon>`, `<scale>` describe how to theme apps the pack has no icon for.
 */
class IconPack private constructor(private val res: Resources, val packageName: String) {

    private val byComponent = LinkedHashMap<String, String>()
    private val byPackage = HashMap<String, String>()
    private val backs = ArrayList<String>()
    private var maskName: String? = null
    private var uponName: String? = null
    private var scale = 1f

    private val hasFallback: Boolean get() = backs.isNotEmpty() || maskName != null || uponName != null

    private fun openAppFilter(): XmlPullParser? {
        val id = res.getIdentifier("appfilter", "xml", packageName)
        if (id != 0) return runCatching { res.getXml(id) }.getOrNull()
        return runCatching {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(res.assets.open("appfilter.xml"), null)
            parser
        }.getOrNull()
    }

    private fun parse() {
        val p = openAppFilter() ?: return
        runCatching {
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "item" -> {
                            val component = p.getAttributeValue(null, "component")
                            val drawable = p.getAttributeValue(null, "drawable")
                            val key = component?.let { componentKey(it) }
                            if (key != null && !drawable.isNullOrEmpty()) {
                                byComponent.putIfAbsent(key, drawable)
                                byPackage.putIfAbsent(key.substringBefore('/'), drawable)
                            }
                        }
                        "iconback" -> for (i in 0 until p.attributeCount) {
                            if (p.getAttributeName(i).startsWith("img")) backs.add(p.getAttributeValue(i))
                        }
                        "iconmask" -> maskName = p.getAttributeValue(null, "img1")
                        "iconupon" -> uponName = p.getAttributeValue(null, "img1")
                        "scale" -> scale = p.getAttributeValue(null, "factor")?.toFloatOrNull() ?: 1f
                    }
                }
                event = p.next()
            }
        }
        (p as? XmlResourceParser)?.close()
    }

    /** "ComponentInfo{pkg/cls}" -> "pkg/cls" (the same key the launcher uses for apps). */
    private fun componentKey(raw: String): String? {
        val inner = (if (raw.contains('{')) raw.substringAfter('{').substringBefore('}') else raw).trim()
        val pkg = inner.substringBefore('/', "")
        var cls = inner.substringAfter('/', "")
        if (pkg.isEmpty() || cls.isEmpty()) return null
        if (cls.startsWith(".")) cls = pkg + cls
        return "$pkg/$cls"
    }

    private fun drawable(name: String): Drawable? {
        var id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) id = res.getIdentifier(name, "mipmap", packageName)
        if (id == 0) return null
        return runCatching { res.getDrawable(id, null) }.getOrNull()
    }

    /** The pack's own icon for this app (its exact activity first, then any icon of the same package). */
    fun exact(pkg: String, cls: String, px: Int): Bitmap? {
        val name = byComponent["$pkg/$cls"] ?: byPackage[pkg] ?: return null
        val d = drawable(name) ?: return null
        return runCatching { d.toBitmap(px, px) }.getOrNull()
    }

    /**
     * For an app the pack has no icon for: draws the app's own icon inside the pack's back / mask / upon
     * layers, so it still matches the pack. Null when the pack defines none of them.
     */
    fun wrap(original: Drawable, key: String, px: Int): Bitmap? {
        if (!hasFallback) return null
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        if (backs.isNotEmpty()) {
            drawable(backs[abs(key.hashCode()) % backs.size])?.let {
                it.setBounds(0, 0, px, px)
                it.draw(canvas)
            }
        }

        val inner = (px * scale).toInt().coerceIn(1, px)
        val offset = ((px - inner) / 2).toFloat()
        val foreground = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val fgCanvas = Canvas(foreground)
        fgCanvas.drawBitmap(original.toBitmap(inner, inner), offset, offset, null)

        // Where the mask is opaque, the icon is cut away.
        maskName?.let { name ->
            drawable(name)?.let { mask ->
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
                fgCanvas.drawBitmap(mask.toBitmap(px, px), 0f, 0f, paint)
            }
        }
        canvas.drawBitmap(foreground, 0f, 0f, null)

        uponName?.let { name ->
            drawable(name)?.let {
                it.setBounds(0, 0, px, px)
                it.draw(canvas)
            }
        }
        return out
    }

    /** Names of every icon in the pack (for the icon picker), sorted. */
    fun allIconNames(): List<String> = byComponent.values.distinct().sorted()

    /** One icon of the pack by name, or null if it cannot be read. */
    fun iconBitmap(name: String, px: Int): Bitmap? {
        val d = drawable(name) ?: return null
        return runCatching { d.toBitmap(px, px) }.getOrNull()
    }

    /** A few icons from the pack, to preview it in the settings. */
    fun sample(count: Int, px: Int): List<Bitmap> {
        val out = ArrayList<Bitmap>()
        for (name in byComponent.values) {
            if (out.size >= count) break
            val d = drawable(name) ?: continue
            runCatching { d.toBitmap(px, px) }.getOrNull()?.let { out.add(it) }
        }
        return out
    }

    companion object {
        fun load(context: Context, packageName: String): IconPack? {
            val res = runCatching { context.packageManager.getResourcesForApplication(packageName) }.getOrNull()
                ?: return null
            return IconPack(res, packageName).also { it.parse() }
        }
    }
}
