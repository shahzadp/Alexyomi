package eu.kanade.presentation.movies

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.toast
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource as composeStringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SLOT_HEIGHT = 64f

@Composable
fun SavedLinksPage(
    context: Context,
    data: SavedLinksData,
    onDataChange: (SavedLinksData) -> Unit,
    onOpenLink: (SavedLink) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var folderDialog by remember { mutableStateOf<String?>(null) } // null closed, "" new, else folder id for rename
    var renameLink by remember { mutableStateOf<SavedLink?>(null) }
    var deleteFolder by remember { mutableStateOf<SavedFolder?>(null) }
    var showAddLink by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importBackup(context, it, data, onDataChange) }
    }

    Scaffold(
        topBar = {
            SavedLinksTopBar(
                onBack = onBack,
                onAddLink = { showAddLink = true },
                onAddFolder = { folderDialog = "" },
                onBackup = { showBackupDialog = true },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (data.links.isEmpty() && data.folders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = composeStringResource(AYMR.strings.movies_saved_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SavedLinksList(
                    data = data,
                    onDataChange = onDataChange,
                    onOpenLink = onOpenLink,
                    onRenameLink = { renameLink = it },
                    onDeleteLink = { link ->
                        onDataChange(data.copy(links = data.links.filterNot { it.url == link.url }))
                        context.toast(AYMR.strings.movies_link_removed)
                    },
                    onRenameFolder = { folder -> folderDialog = folder.id },
                    onDeleteFolder = { deleteFolder = it },
                )
            }
        }
    }

    // --- Folder create/rename dialog ---
    folderDialog?.let { folderId ->
        val existing = data.folders.firstOrNull { it.id == folderId }
        TextInputDialog(
            title = composeStringResource(
                if (existing == null) AYMR.strings.movies_add_folder else AYMR.strings.movies_rename_folder,
            ),
            label = composeStringResource(AYMR.strings.movies_folder_name),
            initial = existing?.name.orEmpty(),
            onDismiss = { folderDialog = null },
            onSave = { name ->
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) {
                    if (existing == null) {
                        val color = SavedLinksStore.nextFolderColor(data.folders.map { it.color })
                        val folder = SavedFolder(SavedLinksStore.newFolderId(), trimmed, color)
                        onDataChange(data.copy(folders = data.folders + folder))
                        context.toast(AYMR.strings.movies_folder_created)
                    } else {
                        onDataChange(
                            data.copy(
                                folders = data.folders.map {
                                    if (it.id == folderId) it.copy(name = trimmed) else it
                                },
                            ),
                        )
                    }
                }
                folderDialog = null
            },
        )
    }

    // --- Rename link dialog (with a copy button for the link address) ---
    renameLink?.let { link ->
        TextInputDialog(
            title = composeStringResource(AYMR.strings.movies_rename_link),
            label = composeStringResource(AYMR.strings.movies_link_name),
            initial = link.name,
            onDismiss = { renameLink = null },
            onSave = { name ->
                val trimmed = name.trim()
                if (trimmed.isNotEmpty()) {
                    onDataChange(
                        data.copy(
                            links = data.links.map { if (it.url == link.url) it.copy(name = trimmed) else it },
                        ),
                    )
                }
                renameLink = null
            },
            below = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = link.url,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            context.copyToClipboard(link.name, link.url)
                            context.toast(AYMR.strings.copied)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = composeStringResource(AYMR.strings.movies_copy_link),
                        )
                    }
                }
            },
        )
    }

    // --- Backup dialog: import and export merged into one button ---
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text(composeStringResource(AYMR.strings.movies_backup)) },
            text = {
                Column {
                    BackupActionRow(
                        icon = Icons.Outlined.Download,
                        label = composeStringResource(AYMR.strings.movies_export_pdf),
                        onClick = {
                            showBackupDialog = false
                            exportBackup(context, data)
                        },
                    )
                    BackupActionRow(
                        icon = Icons.Outlined.Add,
                        label = composeStringResource(AYMR.strings.movies_import_pdf),
                        onClick = {
                            showBackupDialog = false
                            importLauncher.launch(arrayOf("application/pdf", "application/octet-stream"))
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text(composeStringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    // --- Add website link dialog ---
    if (showAddLink) {
        AddWebsiteLinkDialog(
            onDismiss = { showAddLink = false },
            onSave = { name, url ->
                val normalized = normalizeLink(url)
                val finalName = name.ifBlank { hostOf(normalized) ?: normalized }
                onDataChange(data.copy(links = data.links + SavedLink(name = finalName, url = normalized)))
                context.toast(AYMR.strings.movies_link_added)
                showAddLink = false
            },
        )
    }

    // --- Delete folder confirm (links are kept, moved back to Unfiled) ---
    deleteFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            title = { Text(composeStringResource(AYMR.strings.movies_delete_folder_title, folder.name)) },
            text = { Text(composeStringResource(AYMR.strings.movies_delete_folder_summary)) },
            confirmButton = {
                TextButton(onClick = {
                    onDataChange(
                        data.copy(
                            folders = data.folders.filterNot { it.id == folder.id },
                            links = data.links.map { if (it.folderId == folder.id) it.copy(folderId = null) else it },
                        ),
                    )
                    deleteFolder = null
                }) {
                    Text(composeStringResource(MR.strings.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFolder = null }) {
                    Text(composeStringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SavedLinksTopBar(
    onBack: () -> Unit,
    onAddLink: () -> Unit,
    onAddFolder: () -> Unit,
    onBackup: () -> Unit,
) {
    AppBar(
        title = composeStringResource(AYMR.strings.movies_saved_links),
        navigateUp = onBack,
        actions = {
            IconButton(onClick = onAddLink) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = composeStringResource(AYMR.strings.movies_add_link),
                )
            }
            IconButton(onClick = onAddFolder) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = composeStringResource(AYMR.strings.movies_add_folder),
                )
            }
            IconButton(onClick = onBackup) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = composeStringResource(AYMR.strings.movies_backup),
                )
            }
        },
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(label) },
                )
                below()
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(composeStringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(composeStringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun SavedLinksList(
    data: SavedLinksData,
    onDataChange: (SavedLinksData) -> Unit,
    onOpenLink: (SavedLink) -> Unit,
    onRenameLink: (SavedLink) -> Unit,
    onDeleteLink: (SavedLink) -> Unit,
    onRenameFolder: (SavedFolder) -> Unit,
    onDeleteFolder: (SavedFolder) -> Unit,
) {
    val slots = remember(data) { buildSlots(data) }
    val density = LocalDensity.current
    val slotHeightPx = with(density) { SLOT_HEIGHT.dp.toPx() }
    val listState = rememberLazyListState()
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "hint") {
            Text(
                text = composeStringResource(AYMR.strings.movies_drag_hint),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        itemsIndexed(slots, key = { _, slot -> slot.key }) { index, slot ->
            val isDragging = dragIndex == index
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .height(SLOT_HEIGHT.dp)
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                        scaleX = if (isDragging) 1.02f else 1f
                        scaleY = if (isDragging) 1.02f else 1f
                        alpha = if (isDragging) 0.95f else 1f
                    }
                    .shadow(if (isDragging) 10.dp else 0.dp, RoundedCornerShape(12.dp))
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp)
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        RoundedCornerShape(12.dp),
                    )
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(index, slot) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                if (slot is Slot.LinkRow) {
                                    dragIndex = index
                                    dragOffsetY = 0f
                                }
                            },
                            onDrag = { _, amount ->
                                dragOffsetY += amount.y
                            },
                            onDragEnd = {
                                val from = dragIndex
                                if (from != null) {
                                    val to = (from + (dragOffsetY / slotHeightPx).toInt())
                                        .coerceIn(0, slots.lastIndex)
                                    if (to != from) {
                                        val dragged = (slots[from] as? Slot.LinkRow)?.link
                                        if (dragged != null) {
                                            onDataChange(moveLink(data, dragged, slots, to))
                                        }
                                    }
                                }
                                dragIndex = null
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                dragIndex = null
                                dragOffsetY = 0f
                            },
                        )
                    },
            ) {
                when (slot) {
                    is Slot.FolderHeader -> FolderHeaderRow(
                        folder = slot.folder,
                        count = slot.count,
                        onRename = { onRenameFolder(slot.folder) },
                        onDelete = { onDeleteFolder(slot.folder) },
                    )

                    Slot.UnfiledHeader -> Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = composeStringResource(AYMR.strings.movies_unfiled),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is Slot.LinkRow -> LinkRow(
                        link = slot.link,
                        onOpen = onOpenLink,
                        onRename = { onRenameLink(slot.link) },
                        onDelete = { onDeleteLink(slot.link) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderHeaderRow(
    folder: SavedFolder,
    count: Int,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color(folder.color)),
        )
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = composeStringResource(AYMR.strings.movies_folder_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRename) {
            Icon(imageVector = Icons.Outlined.Edit, contentDescription = composeStringResource(AYMR.strings.movies_rename_folder))
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Outlined.Delete, contentDescription = composeStringResource(MR.strings.action_delete))
        }
    }
}

@Composable
private fun LinkRow(
    link: SavedLink,
    onOpen: (SavedLink) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpen(link) }
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = link.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = link.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRename) {
            Icon(imageVector = Icons.Outlined.Edit, contentDescription = composeStringResource(AYMR.strings.movies_rename_link))
        }
        IconButton(onClick = onDelete) {
            Icon(imageVector = Icons.Outlined.Clear, contentDescription = composeStringResource(AYMR.strings.movies_remove_link))
        }
    }
}

@Composable
private fun BackupActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AddWebsiteLinkDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, url: String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(composeStringResource(AYMR.strings.movies_add_website_link)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text(composeStringResource(AYMR.strings.movies_link_url)) },
                    placeholder = { Text("example.com") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(composeStringResource(AYMR.strings.movies_link_name)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onSave(name.trim(), url.trim()) },
            ) {
                Text(composeStringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(composeStringResource(MR.strings.action_cancel))
            }
        },
    )
}

private fun normalizeLink(raw: String): String {
    val t = raw.trim()
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    if (t.startsWith("www.")) return "https://$t"
    return "https://$t"
}

private fun hostOf(url: String): String? =
    runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()

private fun exportBackup(context: Context, data: SavedLinksData) {
    runCatching {
        val bytes = SavedLinksPdf.exportBytes(data)
        val name = "alexyomi_saved_links_backup_${
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        }.pdf"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create the download entry")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Unable to open the download stream")
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            dir?.mkdirs()
            java.io.File(dir, name).writeBytes(bytes)
        }
        context.toast(AYMR.strings.movies_backup_saved)
    }.onFailure { e ->
        android.util.Log.e("MoviesSavedLinks", "Failed to export backup", e)
        context.toast(AYMR.strings.movies_backup_failed)
    }
}

private fun importBackup(
    context: Context,
    uri: Uri,
    data: SavedLinksData,
    onDataChange: (SavedLinksData) -> Unit,
) {
    runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Unable to read the file")
        val merged = SavedLinksPdf.importBytes(data, bytes)
        if (merged == null) {
            context.toast(AYMR.strings.movies_import_failed)
            return@runCatching
        }
        val added = merged.links.size - data.links.size
        onDataChange(merged)
        context.toast(context.stringResource(AYMR.strings.movies_import_done, added))
    }.onFailure { e ->
        android.util.Log.e("MoviesSavedLinks", "Failed to import backup", e)
        context.toast(AYMR.strings.movies_import_failed)
    }
}

private sealed interface Slot {
    val key: String

    data class FolderHeader(val folder: SavedFolder, val count: Int) : Slot {
        override val key: String get() = "folder:${folder.id}"
    }

    data class LinkRow(val link: SavedLink) : Slot {
        override val key: String get() = "link:${link.url}"
    }

    data object UnfiledHeader : Slot {
        override val key: String = "unfiled"
    }
}

private fun buildSlots(data: SavedLinksData): List<Slot> {
    val slots = mutableListOf<Slot>()
    for (folder in data.folders) {
        val folderLinks = data.links.filter { it.folderId == folder.id }
        slots += Slot.FolderHeader(folder, folderLinks.size)
        slots += folderLinks.map { Slot.LinkRow(it) }
    }
    val unfiled = data.links.filter { it.folderId == null }
    slots += Slot.UnfiledHeader
    slots += unfiled.map { Slot.LinkRow(it) }
    return slots
}

/**
 * Moves the link into the section that owns the slot at [to].
 * Folder ownership is decided by the nearest header at or above the target slot.
 */
private fun moveLink(
    data: SavedLinksData,
    link: SavedLink,
    slots: List<Slot>,
    to: Int,
): SavedLinksData {
    var folderId: String? = null
    for (i in to downTo 0) {
        when (val s = slots[i]) {
            is Slot.FolderHeader -> {
                folderId = s.folder.id
                break
            }
            Slot.UnfiledHeader -> {
                folderId = null
                break
            }
            else -> Unit
        }
    }

    val currentOrder = slots.filterIsInstance<Slot.LinkRow>().map { it.link }.toMutableList()
    val fromIndex = currentOrder.indexOfFirst { it.url == link.url }
    if (fromIndex < 0) return data
    currentOrder.removeAt(fromIndex)

    // Insert at the position of the target slot counted among link slots only.
    val targetOrdinal = slots.take(to + 1).count { it is Slot.LinkRow } - 1
    val insertAt = targetOrdinal.coerceIn(0, currentOrder.size)
    currentOrder.add(insertAt, link.copy(folderId = folderId))

    return data.copy(links = currentOrder)
}
