@file:OptIn(ExperimentalFoundationApi::class)

package app.uilauncher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.uilauncher.LauncherViewModel
import app.uilauncher.R
import app.uilauncher.data.AppInfo
import app.uilauncher.data.LauncherSettings

@Composable
fun DrawerOverlay(vm: LauncherViewModel) {
    AnimatedVisibility(
        visible = vm.drawerOpen,
        enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut(),
    ) {
        DrawerContent(vm)
    }
}

@Composable
private fun DrawerContent(vm: LauncherViewModel) {
    val settings by vm.settings.collectAsState()
    val apps by vm.repo.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    BackHandler { if (query.isNotEmpty()) query = "" else vm.closeDrawer() }
    LaunchedEffect(Unit) {
        if (vm.drawerFocusSearch) runCatching { focusRequester.requestFocus() }
    }

    val needle = query.trim()
    val visible = remember(apps, settings.hiddenApps, needle) {
        apps.filter { it.key !in settings.hiddenApps && (needle.isEmpty() || it.label.contains(needle, ignoreCase = true)) }
    }

    // Pulling down while the list is at the top closes the drawer.
    val closePx = with(density) { 96.dp.toPx() }
    val pullToClose = remember(closePx) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > closePx) {
                        pulled = 0f
                        vm.closeDrawer()
                    }
                } else {
                    pulled = 0f
                }
                return Offset.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // While an icon is being dragged out, the drawer stays composed (it owns the gesture) but is invisible.
            .graphicsLayer { alpha = if (vm.drawerHiddenForDrag) 0f else 1f }
            .background(MaterialTheme.colorScheme.surface.copy(alpha = settings.drawerBgOpacity / 100f))
            .pointerInput(Unit) {}
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Header: swiping down on it closes the drawer too.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total > closePx / 2) vm.closeDrawer() },
                        onDragCancel = {},
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear))
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { visible.firstOrNull()?.let { vm.launch(it) } }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            IconButton(onClick = { vm.openSettings() }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
        }

        if (settings.drawerHorizontal) {
            HorizontalDrawer(vm, settings, visible)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(settings.drawerCols),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.fillMaxSize().nestedScroll(pullToClose),
            ) {
                items(visible, key = { it.key }) { app ->
                    DrawerCell(vm, settings, app, Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun HorizontalDrawer(vm: LauncherViewModel, settings: LauncherSettings, visible: List<AppInfo>) {
    val cols = settings.drawerCols
    val rows = settings.drawerRows
    val pages = visible.chunked(cols * rows)
    val pagerState = rememberPagerState(pageCount = { maxOf(1, pages.size) })
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            val page = pages.getOrElse(index) { emptyList() }
            Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                for (r in 0 until rows) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        for (c in 0 until cols) {
                            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                val app = page.getOrNull(r * cols + c)
                                if (app != null) DrawerCell(vm, settings, app, Modifier)
                            }
                        }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    val selected = i == pagerState.currentPage
                    Box(
                        Modifier
                            .padding(3.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 0.9f else 0.35f),
                                CircleShape,
                            )
                    )
                }
            }
        }
    }
}

/**
 * One app in the drawer.
 * - Tap: launch.
 * - Long press: the options menu.
 * - Long press, then move the finger: the menu closes, the drawer hides and the icon follows the
 *   finger over the home screen and dock; lifting the finger drops it there.
 * The whole thing is one gesture so the finger keeps being tracked after the drawer is hidden.
 */
@Composable
private fun DrawerCell(vm: LauncherViewModel, settings: LauncherSettings, app: AppInfo, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptics = LocalHapticFeedback.current
    val currentApp by rememberUpdatedState(app)

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        AppItem(
            app = app,
            iconSize = settings.drawerIconSize,
            showLabel = settings.drawerShowLabels,
            labelSize = settings.drawerLabelSize,
            textColor = MaterialTheme.colorScheme.onSurface,
            shadow = false,
            modifier = Modifier
                .onGloballyPositioned { coordinates = it }
                .pointerInput(Unit) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val longPress = awaitLongPressOrCancellation(down.id)
                        if (longPress == null) {
                            val cur = currentEvent.changes.firstOrNull { it.id == down.id }
                            // Finger lifted without moving much: a tap. (If it moved, the list scrolls.)
                            if (cur != null && !cur.pressed && (cur.position - down.position).getDistance() <= slop * 2) {
                                vm.launch(currentApp)
                            }
                        } else {
                            longPress.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menu = true
                            var dragging = false
                            var finished = false
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    val root = coordinates?.localToRoot(change.position) ?: continue
                                    if (!dragging && (change.position - longPress.position).getDistance() > slop * 2) {
                                        dragging = true
                                        menu = false
                                        vm.startDrag(currentApp, root)
                                    }
                                    if (dragging) vm.dragTo(root)
                                }
                                finished = true
                            } finally {
                                // Normal end: drop. Interrupted (icon left composition): drop nothing.
                                if (dragging) {
                                    if (finished) vm.endDrag() else vm.cancelDrag()
                                }
                            }
                        }
                    }
                }
                .padding(4.dp),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_home)) },
                onClick = { menu = false; vm.addToHome(app); vm.closeDrawer() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_to_dock)) },
                onClick = { menu = false; vm.addToDock(app) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.hide)) },
                onClick = { menu = false; vm.hideApp(app) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.app_info)) },
                onClick = { menu = false; vm.openAppInfo(app) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.uninstall)) },
                onClick = { menu = false; vm.uninstall(app) },
            )
        }
    }
}
