@file:OptIn(ExperimentalFoundationApi::class)

package app.uilauncher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.uilauncher.LauncherViewModel
import app.uilauncher.R
import app.uilauncher.data.AppInfo
import app.uilauncher.data.Folder

/** A folder on the home screen: a rounded tile showing up to four of its apps, with its name below. */
@Composable
fun FolderItem(
    folder: Folder,
    apps: Map<String, AppInfo>,
    iconSize: Int,
    showLabel: Boolean,
    labelSize: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(iconSize.dp)
                .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape((iconSize * 0.28f).dp))
                .padding((iconSize * 0.1f).dp),
        ) {
            val mini = (iconSize * 0.36f).dp
            val inner = folder.apps.mapNotNull { apps[it] }.take(4)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                for (rowApps in inner.chunked(2)) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        for (a in rowApps) AppIcon(a, mini)
                    }
                }
            }
        }
        if (showLabel) {
            val name = folder.name.ifBlank { stringResource(R.string.folder_default_name) }
            Text(
                text = name,
                color = Color.White,
                fontSize = labelSize.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f)),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** The popup shown when a folder is opened: its name (editable) and its apps. */
@Composable
fun FolderOverlay(vm: LauncherViewModel) {
    val id = vm.openFolderId ?: return
    val layout by vm.layout.collectAsState()
    val apps by vm.appMap.collectAsState()
    val folder = layout.folder(id) ?: return

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.closeFolder() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(Color(0xE61E1E1E), RoundedCornerShape(28.dp))
                // Swallow taps on the card so they do not close the folder.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            var name by remember(id) { mutableStateOf(folder.name) }
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(30); vm.renameFolder(id, name) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 20.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (name.isEmpty()) {
                            Text(
                                stringResource(R.string.folder_default_name),
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 20.sp,
                            )
                        }
                        inner()
                    }
                },
            )
            val inside = folder.apps.mapNotNull { apps[it] }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                items(inside, key = { it.key }) { app ->
                    var menu by remember { mutableStateOf(false) }
                    Box(contentAlignment = Alignment.Center) {
                        AppItem(
                            app = app,
                            iconSize = 52,
                            showLabel = true,
                            labelSize = 11,
                            modifier = Modifier
                                .combinedClickable(onClick = { vm.launch(app) }, onLongClick = { menu = true })
                                .padding(6.dp),
                        )
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.take_out_of_folder)) },
                                onClick = { menu = false; vm.takeOutOfFolder(id, app.key) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_icon)) },
                                onClick = { menu = false; vm.editIcon(app) },
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
            }
        }
    }
}
