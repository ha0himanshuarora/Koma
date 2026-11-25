package app.koma.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import app.koma.launcher.data.AppInfo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A folder opened from the app drawer: a frosted card with the folder's name, its widgets, then its apps as a list.
 * Tap an app to open it. EDIT lets you rename the folder and remove things; the footer adds apps or widgets.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderCard(state: HomeState, onMenu: (AppInfo) -> Unit = {}, onPopup: (AppInfo, Float) -> Unit = { _, _ -> }) {
    val pal = state.palette
    val id = state.openFolderId ?: return
    val folder = state.folders.firstOrNull { it.id == id } ?: return
    var editing by remember(id) { mutableStateOf(folder.items.isEmpty()) }
    var name by remember(id) { mutableStateOf(folder.name) }
    var adding by remember(id) { mutableStateOf(false) }
    val none = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(28.dp)
    val byKey = remember(state.apps) { state.apps.associateBy { it.key } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = none, indication = null) { state.openFolderId = null },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(pal.ground.copy(alpha = 0.94f))
                .border(1.dp, pal.onGround.copy(alpha = 0.16f), shape)
                .clickable(interactionSource = none, indication = null) {}
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon("all", 42.dp, pal.tiles[0], pal.ink, pal.onGround)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (editing) Field(
                        value = name,
                        onChange = {
                            name = it
                            state.renameFolder(id, it)
                        },
                        hint = "Folder name",
                        style = dispStyle(pal.onGround, 22),
                    ) else BasicText(folder.name.ifBlank { "Folder" }, style = dispStyle(pal.onGround, 22), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val nApps = folder.items.count { !it.widget }
                    val nWidgets = folder.items.count { it.widget }
                    BasicText(
                        "$nApps APP${if (nApps == 1) "" else "S"}" + if (nWidgets > 0) "  /  $nWidgets WIDGET${if (nWidgets == 1) "" else "S"}" else "",
                        style = monoStyle(pal.onGround.copy(alpha = 0.55f), 9), maxLines = 1,
                    )
                }
                // One small add button: opens a compact choice of what to add.
                val turn by androidx.compose.animation.core.animateFloatAsState(if (adding) 45f else 0f, label = "plus")
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(pal.tiles[0]).clickable { adding = !adding },
                    contentAlignment = Alignment.Center,
                ) { BasicText("+", style = dispStyle(pal.ink, 22), modifier = Modifier.graphicsLayer { rotationZ = turn }) }
                Spacer(Modifier.width(8.dp))
                Pill(if (editing) "DONE" else "EDIT", if (editing) pal.tiles[0] else pal.onGround.copy(alpha = 0.14f), if (editing) pal.ink else pal.onGround, height = 32) {
                    editing = !editing
                }
            }

            if (adding) Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.onGround.copy(alpha = 0.08f)),
            ) {
                listOf("APPS" to "all", "WIDGET" to "widget").forEach { (label, glyph) ->
                    Row(
                        Modifier.weight(1f).clickable {
                            adding = false
                            if (label == "APPS") state.openDrawer(pickFor = Pick("folder", id))
                            else {
                                state.folderTarget = id
                                state.showWidgets = true
                            }
                        }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(glyph, pal.onGround, 18.dp, 2f)
                        Spacer(Modifier.width(8.dp))
                        BasicText(label, style = monoStyle(pal.onGround, 11, bold = true))
                    }
                }
            }

            if (folder.items.isEmpty()) {
                BasicText("Empty. Tap + to add apps or widgets.", style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 14), modifier = Modifier.padding(vertical = 8.dp))
            }

            // Widgets first, drawn by their own apps.
            folder.items.filter { it.widget }.forEach { item ->
                val wid = item.arg.toIntOrNull() ?: return@forEach
                Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(22.dp))) {
                    WidgetTile(state, wid, pal.all[1 % pal.all.size], pal.ink)
                    if (editing) Box(
                        Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(pal.onGround)
                            .clickable { state.removeFolderItem(id, item) },
                        contentAlignment = Alignment.Center,
                    ) { Glyph("close", pal.ground, 14.dp, 3f) }
                }
            }

            // Then the apps as a list.
            folder.items.filter { !it.widget }.forEach { item ->
                val app = byKey[item.arg] ?: return@forEach
                val rowTop = remember { floatArrayOf(0f) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .onGloballyPositioned { rowTop[0] = it.positionInRoot().y }
                        // Same as the drawer list: long-press for the app's menu, swipe along for its pop-up of notifications.
                        .combinedClickable(
                            onClick = {
                                state.launchThenHome(app.key)
                            },
                            onLongClick = { onMenu(app) },
                        )
                        .pointerInput(app.key, state.prefs.leftHanded) {
                            var dx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dx = 0f },
                                onDragEnd = {
                                    if ((if (state.prefs.leftHanded) -dx else dx) > 48.dp.toPx()) onPopup(app, rowTop[0] + size.height)
                                },
                                onDragCancel = {},
                            ) { _, d -> dx += d }
                        }
                        .padding(vertical = 5.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(state.icons, app, 42.dp, bg = pal.all[abs(app.key.hashCode()) % pal.all.size], ink = pal.ink, line = pal.onGround)
                    Spacer(Modifier.width(14.dp))
                    BasicText(app.label, style = bodyStyle(pal.onGround, 17, medium = true), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (editing) SmallIcon("close") { state.removeFolderItem(id, item) }
                }
            }

            if (editing) SwitchRow("Hide from app drawer", "Apps here only show in this folder and in search", folder.hideApps) { state.setFolderHide(id, it) }
            if (editing) Pill("DELETE FOLDER", pal.onGround.copy(alpha = 0.08f), pal.onGround.copy(alpha = 0.8f), Modifier.fillMaxWidth(), height = 40) {
                state.deleteFolder(id)
            }
        }
    }
}
