package app.uilauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.uilauncher.LauncherViewModel
import app.uilauncher.R
import app.uilauncher.data.AppInfo
import app.uilauncher.data.IconPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class EditMode { Menu, Pack, Crop }

private const val OUT_PX = 192

/**
 * Full-screen editor for one app's icon, opened from the app's long-press menu:
 * pick an icon from the applied icon pack, pick (and crop) an image from the gallery, or go back to the
 * original. The result is used for that app in the home screen, the dock and the drawer.
 */
@Composable
fun IconEditor(vm: LauncherViewModel) {
    val app = vm.iconEditApp ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember(app.key) { mutableStateOf(EditMode.Menu) }
    var picked by remember(app.key) { mutableStateOf<Bitmap?>(null) }

    // Back steps out of the pack / crop screens first, then closes the editor.
    val onBack: () -> Unit = {
        if (mode != EditMode.Menu) {
            mode = EditMode.Menu
            picked = null
        } else {
            vm.closeIconEditor()
        }
    }
    BackHandler(onBack = onBack)

    // The system photo picker needs no storage permission.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { decodeImage(context, uri) }
                if (bitmap != null) {
                    picked = bitmap
                    mode = EditMode.Crop
                } else {
                    Toast.makeText(context, R.string.image_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val title = when (mode) {
        EditMode.Menu -> stringResource(R.string.edit_icon)
        EditMode.Pack -> stringResource(R.string.icon_from_pack)
        EditMode.Crop -> stringResource(R.string.crop_title)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            SettingsHeader(title, onBack)
            when (mode) {
                EditMode.Menu -> EditorMenu(
                    vm = vm,
                    app = app,
                    onPack = { mode = EditMode.Pack },
                    onGallery = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                EditMode.Pack -> {
                    val pack = remember { vm.repo.currentPack() }
                    if (pack != null) {
                        PackPane(pack) { bitmap -> vm.saveCustomIcon(app, bitmap, shapeable = false) }
                    }
                }
                EditMode.Crop -> {
                    val source = picked
                    if (source != null) {
                        CropPane(source, onCancel = { picked = null; mode = EditMode.Menu }) { bitmap ->
                            vm.saveCustomIcon(app, bitmap, shapeable = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorMenu(vm: LauncherViewModel, app: AppInfo, onPack: () -> Unit, onGallery: () -> Unit) {
    // Read again whenever any icon changes.
    val version by vm.repo.iconVersion.collectAsState()
    val pack = remember(version) { vm.repo.currentPack() }
    val hasCustom = remember(version) { vm.repo.hasCustomIcon(app.key) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 72.dp)
            Text(app.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
        }
        NavRow(
            title = stringResource(R.string.icon_from_pack),
            summary = if (pack == null) stringResource(R.string.icon_from_pack_need) else null,
        ) { if (pack != null) onPack() }
        NavRow(title = stringResource(R.string.icon_from_gallery), onClick = onGallery)
        if (hasCustom) {
            NavRow(title = stringResource(R.string.icon_restore)) { vm.restoreOriginalIcon(app) }
        }
    }
}

/** Every icon of the applied pack, with a search box. Tapping one uses it for the app. */
@Composable
private fun PackPane(pack: IconPack, onPick: (Bitmap) -> Unit) {
    var query by remember { mutableStateOf("") }
    val names by produceState(emptyList<String>(), pack) {
        value = withContext(Dispatchers.Default) { pack.allIconNames() }
    }
    val shown = remember(names, query) {
        val q = query.trim()
        if (q.isEmpty()) names else names.filter { it.contains(q, ignoreCase = true) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text(stringResource(R.string.search_icons)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(columns = GridCells.Adaptive(64.dp), modifier = Modifier.weight(1f)) {
            items(shown, key = { it }) { name -> PackIconCell(pack, name, onPick) }
        }
    }
}

@Composable
private fun PackIconCell(pack: IconPack, name: String, onPick: (Bitmap) -> Unit) {
    val bitmap by produceState<Bitmap?>(null, name) {
        value = withContext(Dispatchers.Default) { pack.iconBitmap(name, OUT_PX) }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(enabled = bitmap != null) { bitmap?.let(onPick) }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(bitmap = it.asImageBitmap(), contentDescription = name, modifier = Modifier.fillMaxSize()) }
    }
}

/** Square crop: drag to move, pinch to zoom. Save gives the visible square as the icon. */
@Composable
private fun CropPane(source: Bitmap, onCancel: () -> Unit, onDone: (Bitmap) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = minOf(maxWidth - 48.dp, 320.dp)
        val sidePx = with(density) { side.toPx() }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.crop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .size(side)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .pointerInput(sidePx) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            // Keep the image covering the whole square.
                            val limit = (newScale - 1f) * sidePx / 2f
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-limit, limit),
                                (offset.y + pan.y).coerceIn(-limit, limit),
                            )
                            scale = newScale
                        }
                    },
            ) {
                Image(
                    bitmap = source.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onDone(cropVisible(source, sidePx, scale, offset)) }) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

/** The part of [src] that the crop square currently shows, scaled to the icon size. */
private fun cropVisible(src: Bitmap, viewportPx: Float, scale: Float, offset: Offset): Bitmap {
    val side = minOf(src.width, src.height).toFloat()
    val fit = viewportPx / side // how much ContentScale.Crop enlarged the image
    val centerX = src.width / 2f - offset.x / (fit * scale)
    val centerY = src.height / 2f - offset.y / (fit * scale)
    val half = side / (2f * scale)
    val from = Rect(
        (centerX - half).roundToInt().coerceAtLeast(0),
        (centerY - half).roundToInt().coerceAtLeast(0),
        (centerX + half).roundToInt().coerceAtMost(src.width),
        (centerY + half).roundToInt().coerceAtMost(src.height),
    )
    val out = Bitmap.createBitmap(OUT_PX, OUT_PX, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(src, from, Rect(0, 0, OUT_PX, OUT_PX), Paint(Paint.FILTER_BITMAP_FLAG))
    return out
}

/** Reads an image from the gallery, no larger than 1024 px (software bitmap so it can be drawn and cropped). */
private fun decodeImage(context: Context, uri: Uri): Bitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        if (longest > 1024) {
            val factor = 1024f / longest
            decoder.setTargetSize(
                (info.size.width * factor).toInt().coerceAtLeast(1),
                (info.size.height * factor).toInt().coerceAtLeast(1),
            )
        }
    }
}.getOrNull()
