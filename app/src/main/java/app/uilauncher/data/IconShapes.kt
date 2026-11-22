package app.uilauncher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * How icons look: which icon pack (empty = none), the shape of the apps' own icons, and the corner
 * radius (percent of the icon size, only used by [IconShapes.ROUNDED]). One style for every section.
 */
data class IconStyle(
    val pack: String = "",
    val shape: Int = IconShapes.ORIGINAL,
    val corner: Int = 30,
)

object IconShapes {
    const val ORIGINAL = 0 // keep the shape the phone gives each icon
    const val CIRCLE = 1
    const val ROUNDED = 2 // rounded square; the corner slider sets how round
    const val SQUIRCLE = 3

    private fun path(shape: Int, corner: Int, size: Float): Path {
        val path = Path()
        val rect = RectF(0f, 0f, size, size)
        when (shape) {
            CIRCLE -> path.addOval(rect, Path.Direction.CW)
            ROUNDED -> {
                val radius = size * corner.coerceIn(0, 50) / 100f
                path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            }
            else -> { // squircle: a superellipse |x|^4 + |y|^4 = 1
                val half = size / 2f
                val steps = 120
                for (i in 0..steps) {
                    val angle = Math.PI * 2 * i / steps
                    val c = cos(angle)
                    val s = sin(angle)
                    val x = half + half * (if (c < 0) -1 else 1) * abs(c).pow(0.5)
                    val y = half + half * (if (s < 0) -1 else 1) * abs(s).pow(0.5)
                    if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
                }
                path.close()
            }
        }
        return path
    }

    /** Draws [original] cut to the chosen shape. [ORIGINAL] returns it unchanged. */
    fun apply(original: Drawable, style: IconStyle, px: Int): Bitmap {
        if (style.shape == ORIGINAL) return original.toBitmap(px, px)

        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.clipPath(path(style.shape, style.corner, px.toFloat()))

        if (original is AdaptiveIconDrawable) {
            // The two layers are 1.5x the visible icon: draw them in full and let our shape cut them.
            val inset = px / 4
            original.background?.let {
                it.setBounds(-inset, -inset, px + inset, px + inset)
                it.draw(canvas)
            }
            original.foreground?.let {
                it.setBounds(-inset, -inset, px + inset, px + inset)
                it.draw(canvas)
            }
        } else {
            // An old-style icon has no layers: put it, smaller, on a white backdrop of the chosen shape.
            canvas.drawColor(Color.WHITE)
            val pad = (px * 0.16f).toInt()
            original.setBounds(pad, pad, px - pad, px - pad)
            original.draw(canvas)
        }
        return out
    }
}
