package app.uilauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.security.MessageDigest

/**
 * Icons the user picked for single apps (an image from the gallery, or an icon from a pack), saved as
 * PNG files in the app's private storage. They override everything else for that app, in every section.
 *
 * A gallery image follows the chosen icon shape ("shapeable"); an icon taken from a pack is kept as is.
 */
class CustomIcons(context: Context) {

    class Custom(val bitmap: Bitmap, val shapeable: Boolean)

    private val dir = File(context.filesDir, "custom_icons").apply { mkdirs() }

    private fun fileName(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun plainFile(key: String) = File(dir, fileName(key) + ".png")

    private fun shapedFile(key: String) = File(dir, fileName(key) + ".s.png")

    fun has(key: String): Boolean = plainFile(key).exists() || shapedFile(key).exists()

    fun load(key: String): Custom? {
        val shaped = shapedFile(key)
        val plain = plainFile(key)
        val file = when {
            shaped.exists() -> shaped
            plain.exists() -> plain
            else -> return null
        }
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
        return Custom(bitmap, file == shaped)
    }

    fun save(key: String, bitmap: Bitmap, shapeable: Boolean) {
        remove(key)
        val file = if (shapeable) shapedFile(key) else plainFile(key)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun remove(key: String) {
        plainFile(key).delete()
        shapedFile(key).delete()
    }
}
