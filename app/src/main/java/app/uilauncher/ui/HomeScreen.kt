@file:OptIn(ExperimentalFoundationApi::class)

package app.uilauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.uilauncher.DropEvent
import app.uilauncher.LauncherViewModel
import app.uilauncher.R
import app.uilauncher.SettingsScreen
import app.uilauncher.data.AppInfo
import app.uilauncher.data.HomeItem
import app.uilauncher.data.HomeLayout
import app.uilauncher.data.LauncherSettings
import app.uilauncher.data.MAX_PAGES
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val settings by vm.settings.collectAsState()
    val layout by vm.layout.collectAsState()
    val apps by vm.appMap.collectAsState()
    val pagerState = rememberPagerState(pageCount = { layout.pages })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { vm.homePage = it }
    }
    LaunchedEffect(Unit) {
        vm.homeEvents.collect { pagerState.animateScrollToPage(0) }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        if (settings.showSearchBar) {
            SearchPill(onClick = { vm.openDrawer(focusSearch = true) })
        }
        HomeGrid(
            vm = vm,
            settings = settings,
            layout = layout,
            apps = apps,
            pagerState = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        PageIndicator(count = layout.pages, current = pagerState.currentPage)
        Dock(vm = vm, settings = settings, layout = layout, apps = apps)
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
        Text(
            text = stringResource(R.string.search_apps),
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun PageIndicator(count: Int, current: Int) {
    if (count <= 1) return
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val selected = i == current
            Box(
                Modifier
                    .padding(3.dp)
                    .size(if (selected) 8.dp else 6.dp)
                    .background(Color.White.copy(alpha = if (selected) 0.95f else 0.45f), CircleShape)
            )
        }
    }
}

/**
 * The paged icon grid. All touch handling (tap, long press, drag, swipe up) lives in one
 * gesture on the whole area, using plain cell math, so dragging works across pages.
 */
@Composable
private fun HomeGrid(
    vm: LauncherViewModel,
    settings: LauncherSettings,
    layout: HomeLayout,
    apps: Map<String, AppInfo>,
    pagerState: PagerState,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val layoutState by rememberUpdatedState(layout)
    val appsState by rememberUpdatedState(apps)

    var dragId by remember { mutableStateOf<String?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var menuItemId by remember { mutableStateOf<String?>(null) }
    var emptyMenu by remember { mutableStateOf<Offset?>(null) }

    // Where this grid sits in the window, to place an icon dragged in from the drawer.
    var gridOrigin by remember { mutableStateOf(Offset.Zero) }
    val externalApp = vm.dragApp

    BoxWithConstraints(modifier.onGloballyPositioned { gridOrigin = it.positionInRoot() }) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val padH = with(density) { settings.homePadH.dp.toPx() }
        val padV = with(density) { settings.homePadV.dp.toPx() }
        val cols = settings.homeCols
        val rows = settings.homeRows
        val cellW = (wPx - 2 * padH) / cols
        val cellH = (hPx - 2 * padV) / rows
        val cellWdp = with(density) { cellW.toDp() }
        val cellHdp = with(density) { cellH.toDp() }

        fun colOf(x: Float) = ((x - padH) / cellW).toInt().coerceIn(0, cols - 1)
        fun rowOf(y: Float) = ((y - padV) / cellH).toInt().coerceIn(0, rows - 1)
        fun itemAt(p: Offset): HomeItem? {
            if (p.x < padH || p.x > wPx - padH || p.y < padV || p.y > hPx - padV) return null
            val c = colOf(p.x)
            val r = rowOf(p.y)
            val page = pagerState.currentPage
            return layoutState.items.firstOrNull {
                it.page == page && it.row == r && it.col == c && appsState.containsKey(it.key)
            }
        }

        // Dropping an app dragged out of the drawer: onto the cell under the finger (or the nearest free one).
        val onDrop by rememberUpdatedState<(DropEvent) -> Unit> { event ->
            val local = event.pos - gridOrigin
            if (local.x in 0f..wPx && local.y in 0f..hPx) {
                val page = pagerState.currentPage
                val target = layoutState.nearestFree(page, rowOf(local.y), colOf(local.x), cols, rows)
                if (target == null) vm.showNoSpace() else vm.addToHomeAt(event.app, page, target.first, target.second)
            }
        }
        LaunchedEffect(Unit) { vm.dropEvents.collect { onDrop(it) } }

        // Hovering at a screen edge while dragging flips the page (and adds one past the last page).
        val edgePx = with(density) { 28.dp.toPx() }
        val activePos: Offset? = when {
            dragId != null -> dragPos
            externalApp != null -> vm.dragPos - gridOrigin
            else -> null
        }
        val edge = when {
            activePos == null || activePos.y < 0f || activePos.y > hPx -> 0
            activePos.x < edgePx -> -1
            activePos.x > wPx - edgePx -> 1
            else -> 0
        }
        LaunchedEffect(edge, pagerState.currentPage) {
            if (edge != 0) {
                delay(550)
                val target = pagerState.currentPage + edge
                if (target >= layoutState.pages && target < MAX_PAGES) {
                    vm.addPage()
                    delay(120)
                }
                if (target in 0 until layoutState.pages) pagerState.animateScrollToPage(target)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(cols, rows, cellW, cellH, padH, padV) {
                    val slop = viewConfiguration.touchSlop
                    val swipePx = 72.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            val cur = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (cur == null || !cur.pressed) {
                                // Finger lifted: a tap.
                                if (cur != null && (cur.position - start).getDistance() <= slop * 2) {
                                    val app = itemAt(start)?.let { appsState[it.key] }
                                    if (app != null) vm.launch(app)
                                }
                            } else {
                                // Finger moved: follow it to detect a swipe up.
                                var last = cur.position
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    last = change.position
                                    if (!change.pressed) break
                                }
                                val dy = last.y - start.y
                                val dx = last.x - start.x
                                if (dy < -swipePx && abs(dy) > abs(dx) * 1.5f) vm.openDrawer()
                            }
                        } else {
                            val item = itemAt(longPress.position)
                            if (item == null) {
                                // Long press on empty space: home menu.
                                emptyMenu = longPress.position
                                longPress.consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                }
                            } else {
                                dragId = item.id
                                dragPos = longPress.position
                                val dragStart = longPress.position
                                var moved = false
                                longPress.consume()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    dragPos += change.position - change.previousPosition
                                    change.consume()
                                    if (!moved && (dragPos - dragStart).getDistance() > slop) moved = true
                                }
                                val id = dragId
                                dragId = null
                                if (id != null) {
                                    if (!moved) {
                                        menuItemId = id
                                    } else {
                                        val page = pagerState.currentPage
                                        val r = rowOf(dragPos.y)
                                        val c = colOf(dragPos.x)
                                        if (layoutState.isFree(page, r, c, id)) vm.moveItem(id, page, r, c)
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = dragId == null && menuItemId == null && emptyMenu == null,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(Modifier.fillMaxSize()) {
                    for (item in layout.items) {
                        if (item.page != page || item.id == dragId) continue
                        val app = apps[item.key] ?: continue
                        Box(
                            Modifier
                                .offset { IntOffset((padH + item.col * cellW).roundToInt(), (padV + item.row * cellH).roundToInt()) }
                                .size(cellWdp, cellHdp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AppItem(app, settings.homeIconSize, settings.homeShowLabels, settings.homeLabelSize)
                        }
                    }
                }
            }

            // Highlights the cell an icon dragged in from the drawer would land on.
            if (externalApp != null) {
                val local = vm.dragPos - gridOrigin
                if (local.x in 0f..wPx && local.y in 0f..hPx) {
                    val target = layout.nearestFree(pagerState.currentPage, rowOf(local.y), colOf(local.x), cols, rows)
                    if (target != null) {
                        Box(
                            Modifier
                                .offset { IntOffset((padH + target.second * cellW).roundToInt(), (padV + target.first * cellH).roundToInt()) }
                                .size(cellWdp, cellHdp)
                                .padding(4.dp)
                                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
                        )
                    }
                }
            }

            // The icon being dragged follows the finger.
            val dragged = layout.items.firstOrNull { it.id == dragId }
            val draggedApp = dragged?.let { apps[it.key] }
            if (draggedApp != null) {
                Box(
                    Modifier
                        .offset { IntOffset((dragPos.x - cellW / 2).roundToInt(), (dragPos.y - cellH / 2).roundToInt()) }
                        .size(cellWdp, cellHdp)
                        .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = 0.92f },
                    contentAlignment = Alignment.Center,
                ) {
                    AppItem(draggedApp, settings.homeIconSize, settings.homeShowLabels, settings.homeLabelSize)
                }
            }

            // Menu for a long-pressed icon.
            val menuItem = layout.items.firstOrNull { it.id == menuItemId }
            val menuApp = menuItem?.let { apps[it.key] }
            if (menuItem != null && menuApp != null) {
                Box(
                    Modifier
                        .offset { IntOffset((padH + menuItem.col * cellW).roundToInt(), (padV + menuItem.row * cellH).roundToInt()) }
                        .size(cellWdp, cellHdp)
                ) {
                    DropdownMenu(expanded = true, onDismissRequest = { menuItemId = null }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.app_info)) },
                            onClick = { menuItemId = null; vm.openAppInfo(menuApp) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.uninstall)) },
                            onClick = { menuItemId = null; vm.uninstall(menuApp) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove)) },
                            onClick = { menuItemId = null; vm.removeItem(menuItem.id) },
                        )
                    }
                }
            }

            // Menu for a long press on empty space.
            val emptyPos = emptyMenu
            if (emptyPos != null) {
                Box(Modifier.offset { IntOffset(emptyPos.x.roundToInt(), emptyPos.y.roundToInt()) }) {
                    DropdownMenu(expanded = true, onDismissRequest = { emptyMenu = null }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_page)) },
                            onClick = { emptyMenu = null; vm.addPage() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remove_page)) },
                            onClick = { emptyMenu = null; vm.removePage(pagerState.currentPage) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.settings)) },
                            onClick = { emptyMenu = null; vm.openSettings() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dock(
    vm: LauncherViewModel,
    settings: LauncherSettings,
    layout: HomeLayout,
    apps: Map<String, AppInfo>,
) {
    val keys = layout.dock.filter { apps.containsKey(it) }.take(settings.dockCount)

    // Dock bounds in the window: used to drop an icon dragged in from the drawer onto a slot.
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val slotCount = settings.dockCount
    fun slotAt(pos: Offset): Int =
        ((pos.x - bounds.left) / (bounds.width / slotCount)).toInt().coerceIn(0, slotCount - 1)
    val onDrop by rememberUpdatedState<(DropEvent) -> Unit> { event ->
        if (bounds.contains(event.pos)) vm.addToDockAt(event.app, slotAt(event.pos))
    }
    LaunchedEffect(Unit) { vm.dropEvents.collect { onDrop(it) } }
    val hoverSlot = if (vm.dragApp != null && bounds.contains(vm.dragPos)) slotAt(vm.dragPos) else -1

    Row(
        Modifier
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = settings.dockBgOpacity / 100f),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            )
            .defaultMinSize(minHeight = (settings.dockIconSize + 20).dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (slot in 0 until settings.dockCount) {
            val app = keys.getOrNull(slot)?.let { apps[it] }
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (slot == hoverSlot) Color.White.copy(alpha = 0.22f) else Color.Transparent,
                        RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (app != null) {
                    key(app.key) {
                        var menu by remember { mutableStateOf(false) }
                        AppItem(
                            app = app,
                            iconSize = settings.dockIconSize,
                            showLabel = settings.dockShowLabels,
                            labelSize = settings.homeLabelSize,
                            modifier = Modifier
                                .combinedClickable(onClick = { vm.launch(app) }, onLongClick = { menu = true })
                                .padding(4.dp),
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.app_info)) },
                                onClick = { menu = false; vm.openAppInfo(app) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.uninstall)) },
                                onClick = { menu = false; vm.uninstall(app) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.remove)) },
                                onClick = { menu = false; vm.removeFromDock(app.key) },
                            )
                        }
                    }
                }
            }
        }
    }
}
