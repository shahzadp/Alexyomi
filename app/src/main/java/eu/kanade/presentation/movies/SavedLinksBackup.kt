package eu.kanade.presentation.movies

import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain-text export/import of the saved links.
 *
 * The export is a human-readable sheet that ends with a machine-readable block
 * (lines prefixed ALX|). The import scans line by line — nothing to decompress,
 * no encoding tricks, and it works with any text file the app wrote.
 */
object SavedLinksBackup {

    fun exportBytes(data: SavedLinksData): ByteArray {
        val sb = StringBuilder()
        sb.appendLine("Alexyomi — Saved Links Backup")
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        sb.appendLine("${data.links.size} links in ${data.folders.size} folders · $date")
        sb.appendLine()

        for (folder in data.folders) {
            val folderLinks = data.links.filter { it.folderId == folder.id }
            if (folderLinks.isEmpty()) continue
            sb.appendLine("== ${folder.name} (${folderLinks.size} links) ==")
            for (link in folderLinks) appendLink(sb, link)
            sb.appendLine()
        }

        val ungrouped = data.links.filter { it.folderId == null }
        if (ungrouped.isNotEmpty()) {
            sb.appendLine("== Ungrouped (${ungrouped.size} links) ==")
            for (link in ungrouped) appendLink(sb, link)
            sb.appendLine()
        }

        sb.appendLine("— machine readable backup (do not edit) —")
        sb.appendLine("ALX|T|v1")
        for (folder in data.folders) {
            sb.appendLine("ALX|F|name=${enc(folder.name)}|color=${folder.color}")
        }
        for (link in data.links) {
            val folderName = data.folders.firstOrNull { it.id == link.folderId }?.name.orEmpty()
            sb.appendLine("ALX|L|folder=${enc(folderName)}|name=${enc(link.name)}|url=${enc(link.url)}")
        }
        sb.appendLine("ALX|E")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun appendLink(sb: StringBuilder, link: SavedLink) {
        sb.appendLine("• ${link.name}")
        sb.appendLine("  ${link.url}")
    }

    /** Returns the merged data, or null if the file is not an Alexyomi backup. */
    fun importBytes(current: SavedLinksData, bytes: ByteArray): SavedLinksData? {
        val text = String(bytes, Charsets.UTF_8)
        val rows = text.lines().map { it.trim() }.filter { it.startsWith("ALX|") }
        if (rows.none { it.startsWith("ALX|T|") }) return null

        val folders = current.folders.toMutableList()
        val usedColors = folders.map { it.color }.toMutableList()

        fun folderFor(name: String, color: Int?): String {
            val existing = folders.firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (existing != null) return existing.id
            val finalColor = if (color != null && color !in usedColors) color
                else SavedLinksStore.nextFolderColor(usedColors)
            usedColors += finalColor
            val id = SavedLinksStore.newFolderId()
            folders += SavedFolder(id, name, finalColor)
            return id
        }

        val pending = mutableListOf<Pair<String?, SavedLink>>() // folder name (or null) + link
        for (row in rows) {
            when {
                row.startsWith("ALX|F|") -> {
                    val f = parseFields(row.removePrefix("ALX|F|"))
                    val name = f["name"]?.let { dec(it) }?.trim().orEmpty()
                    val color = f["color"]?.toIntOrNull()
                    if (name.isNotEmpty()) folderFor(name, color)
                }
                row.startsWith("ALX|L|") -> {
                    val f = parseFields(row.removePrefix("ALX|L|"))
                    val folderName = f["folder"]?.let { dec(it) }?.trim()
                    val name = f["name"]?.let { dec(it) }?.trim().orEmpty()
                    val url = f["url"]?.let { dec(it) }?.trim().orEmpty()
                    if (url.isNotEmpty()) {
                        pending += folderName?.takeIf { it.isNotEmpty() } to SavedLink(
                            name = name.ifEmpty { url },
                            url = url,
                        )
                    }
                }
                row.startsWith("ALX|E") -> break
            }
        }

        val existingUrls = current.links.map { it.url }.toMutableSet()
        val mergedLinks = current.links.toMutableList()
        for ((folderName, link) in pending) {
            if (link.url in existingUrls) continue // duplicate: keep the existing entry
            val folderId = folderName?.let { folderFor(it, null) }
            mergedLinks += link.copy(folderId = folderId)
            existingUrls += link.url
        }
        return current.copy(folders = folders, links = mergedLinks)
    }

    private fun parseFields(s: String): Map<String, String> =
        s.split('|').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")
}
