package eu.kanade.presentation.movies

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PDF export/import of the saved links. The export is a pretty human-readable sheet
 * that ALSO embeds an invisible machine-readable block (drawn off-page) so the same
 * app can re-import its own backups.
 */
object SavedLinksPdf {

    private const val PAGE_W = 595 // A4 @72dpi
    private const val PAGE_H = 842
    private const val MARGIN_X = 44f
    private const val MARGIN_TOP = 48f
    private const val MARGIN_BOTTOM = 44f

    private val titlePaint = Paint().apply {
        textSize = 19f
        isFakeBoldText = true
        color = Color.BLACK
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    private val metaPaint = Paint().apply {
        textSize = 9.5f
        color = Color.rgb(0x66, 0x66, 0x66)
        isAntiAlias = true
    }
    private val headPaint = Paint().apply {
        textSize = 13f
        isFakeBoldText = true
        color = Color.BLACK
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }
    private val rowPaint = Paint().apply {
        textSize = 10.5f
        color = Color.BLACK
        isAntiAlias = true
    }
    private val subPaint = Paint().apply {
        textSize = 8f
        color = Color.rgb(0x55, 0x55, 0x55)
        isAntiAlias = true
    }
    private val dotPaint = Paint().apply { isAntiAlias = true }

    fun exportBytes(data: SavedLinksData): ByteArray {
        val pdf = PdfDocument()
        var pageNum = 0
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNum).create())
        var canvas: Canvas = page.canvas
        var y = MARGIN_TOP
        val maxWidth = PAGE_W - MARGIN_X * 2

        fun newPage() {
            pdf.finishPage(page)
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNum).create())
            canvas = page.canvas
            y = MARGIN_TOP
        }

        fun ensure(height: Float) {
            if (y + height > PAGE_H - MARGIN_BOTTOM) {
                newPage()
            }
        }

        fun drawDot(color: Int) {
            dotPaint.color = color
            dotPaint.style = Paint.Style.FILL
            canvas.drawCircle(MARGIN_X + 6f, y - 5f, 5f, dotPaint)
        }

        fun wrap(text: String, paint: Paint, width: Float): List<String> {
            if (paint.measureText(text) <= width) return listOf(text)
            val words = text.split(' ')
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= width) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) lines.add(current.toString())
            val out = mutableListOf<String>()
            for (line in lines) {
                if (paint.measureText(line) <= width) {
                    out.add(line)
                    continue
                }
                var rest = line
                while (rest.isNotEmpty() && paint.measureText(rest) > width) {
                    var cut = rest.length
                    while (cut > 1 && paint.measureText(rest.substring(0, cut)) > width) cut--
                    out.add(rest.substring(0, cut))
                    rest = rest.substring(cut)
                }
                if (rest.isNotEmpty()) out.add(rest)
            }
            return out
        }

        fun text(t: String, x: Float, paint: Paint) {
            canvas.drawText(t, x, y, paint)
        }

        fun sectionHeader(name: String, color: Int?, count: Int) {
            ensure(40f)
            if (color != null) {
                drawDot(color)
                y += 0f
            } else {
                canvas.drawText("•", MARGIN_X, y - 4f, headPaint)
            }
            val prefix = if (color != null) "    " else "    "
            text(prefix + name + "  ($count links)", MARGIN_X, headPaint)
            y += 20f
        }

        fun linkRow(link: SavedLink) {
            val nameLines = wrap(link.name, rowPaint, maxWidth - 28f)
            val urlLines = wrap(link.url, subPaint, maxWidth - 40f)
            ensure(15f * nameLines.size + 13f * urlLines.size + 10f)
            for (line in nameLines) {
                text("• $line", MARGIN_X + 16f, rowPaint)
                y += 15f
            }
            for (line in urlLines) {
                text(line, MARGIN_X + 28f, subPaint)
                y += 13f
            }
            y += 4f
        }

        // --- Header ---
        text("Alexyomi — Saved Links", MARGIN_X, titlePaint)
        y += 17f
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        text("${data.links.size} links in ${data.folders.size} folders · $date", MARGIN_X, metaPaint)
        y += 28f

        // --- Folders ---
        for (folder in data.folders) {
            val folderLinks = data.links.filter { it.folderId == folder.id }
            if (folderLinks.isEmpty()) continue
            sectionHeader(folder.name, folder.color, folderLinks.size)
            folderLinks.forEach { linkRow(it) }
            y += 8f
        }

        // --- Unfiled ---
        val unfiled = data.links.filter { it.folderId == null }
        if (unfiled.isNotEmpty()) {
            sectionHeader("Unfiled", null, unfiled.size)
            unfiled.forEach { linkRow(it) }
        }

        // --- Invisible machine-readable data for re-import ---
        // A dedicated final page, drawn in white 5pt text on the white page: invisible to a
        // reader, but fully present in the PDF content stream. Import scans for these lines.
        fun newPageWhite() {
            pdf.finishPage(page)
            page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, ++pageNum).create())
            canvas = page.canvas
            y = MARGIN_TOP
        }
        val machineLines = mutableListOf("ALX|T|v1")
        for (folder in data.folders) {
            machineLines += "ALX|F|name=${enc(folder.name)}|color=${folder.color}"
        }
        for (link in data.links) {
            val folderName = data.folders.firstOrNull { it.id == link.folderId }?.name.orEmpty()
            machineLines += "ALX|L|folder=${enc(folderName)}|name=${enc(link.name)}|url=${enc(link.url)}"
        }
        machineLines += "ALX|E"

        newPageWhite()
        val ghostPaint = Paint().apply {
            textSize = 5f
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawText("Alexyomi backup data", MARGIN_X, y, rowPaint)
        y += 10f
        for (line in machineLines) {
            canvas.drawText(line, MARGIN_X, y, ghostPaint)
            y += 6f
        }

        pdf.finishPage(page)
        val out = ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }

    /** Returns the merged data, or null if the file is not an Alexyomi backup PDF. */
    fun importBytes(current: SavedLinksData, bytes: ByteArray): SavedLinksData? {
        val text = String(bytes, Charsets.ISO_8859_1)
        // The machine block lines are pure ASCII ALX|... — find them anywhere in the file
        // without depending on how the PDF generator encodes its text operators.
        val rows = ALX_LINE.findAll(text).map { it.value }.toList()
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
                row == "ALX|E" -> break
            }
        }

        val existingUrls = current.links.map { it.url }.toMutableSet()
        val mergedLinks = current.links.toMutableList()
        var skipped = 0
        for ((folderName, link) in pending) {
            if (link.url in existingUrls) {
                skipped++
                continue
            }
            val folderId = folderName?.let { folderFor(it, null) }
            mergedLinks += link.copy(folderId = folderId)
            existingUrls += link.url
        }
        return current.copy(folders = folders, links = mergedLinks)
    }

    private val ALX_LINE = Regex("""ALX\|[TFLTE]\|[^\s()\\\r\n]*""")

    private fun parseFields(s: String): Map<String, String> =
        s.split('|').mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx <= 0) null else part.substring(0, idx) to part.substring(idx + 1)
        }.toMap()

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    private fun dec(s: String): String = URLDecoder.decode(s, "UTF-8")
}
