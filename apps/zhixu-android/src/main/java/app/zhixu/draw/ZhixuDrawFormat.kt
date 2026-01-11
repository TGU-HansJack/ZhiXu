package app.zhixu.draw

import android.util.Xml
import androidx.compose.ui.geometry.Offset
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

object ZhixuDrawFormat {
    const val MIME_TYPE: String = "application/zhixu-drawing"
    const val EXTENSION: String = ".zhixu"
    const val LEGACY_EXTENSION: String = ".zhixud"

    fun hasDrawingExtension(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return (lower.endsWith(EXTENSION) && lower.length > EXTENSION.length) ||
            (lower.endsWith(LEGACY_EXTENSION) && lower.length > LEGACY_EXTENSION.length)
    }

    fun stripDrawingExtension(name: String): String {
        val lower = name.lowercase(Locale.US)
        return when {
            lower.endsWith(EXTENSION) && lower.length > EXTENSION.length -> name.dropLast(EXTENSION.length)
            lower.endsWith(LEGACY_EXTENSION) && lower.length > LEGACY_EXTENSION.length -> name.dropLast(LEGACY_EXTENSION.length)
            else -> name
        }
    }

    fun encode(document: ZhixuDrawDocument): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            // OpenDocument-style: mimetype first.
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write(MIME_TYPE.toByteArray(Charsets.US_ASCII))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("META-INF/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("META-INF/version"))
            zos.write("current=1\nmin=1\n".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("pages/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("assets/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("assets/images/"))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("assets/pdf/"))
            zos.closeEntry()

            val pageFiles = document.pages.mapIndexed { index, page -> pageFileName(index) to page }

            val metaJson = buildMetaJson(document.meta.copy(pageOrder = pageFiles.map { it.first }))
            zos.putNextEntry(ZipEntry("meta.json"))
            zos.write(metaJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            for ((fileName, page) in pageFiles) {
                val pageJson = buildPageJson(page)
                zos.putNextEntry(ZipEntry(fileName))
                zos.write(pageJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): ZhixuDrawDocument {
        require(bytes.isNotEmpty()) { "Empty file" }

        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (!entry.isDirectory) {
                    entries[entry.name] = zis.readBytes()
                }
            }
        }

        val metaBytes = entries["meta.json"]
        if (metaBytes != null) {
            val metaObj = JSONObject(metaBytes.toString(Charsets.UTF_8))
            val meta = parseMeta(metaObj)

            val pages = ArrayList<ZhixuDrawPage>(meta.pageOrder.size)
            for (file in meta.pageOrder) {
                val pageBytes = entries[file] ?: continue
                val pageObj = JSONObject(pageBytes.toString(Charsets.UTF_8))
                pages += parsePage(pageObj)
            }
            if (pages.isEmpty()) error("No pages found")
            return ZhixuDrawDocument(meta = meta.copy(pageOrder = meta.pageOrder), pages = pages)
        }

        val contentXml = entries["content.xml"]?.toString(Charsets.UTF_8)
        if (contentXml != null) {
            return decodeLegacyContentXml(contentXml)
        }

        error("Invalid drawing file: missing meta.json/content.xml")
    }

    fun pageFileName(index: Int): String = "pages/page_${(index + 1).toString().padStart(3, '0')}.json"

    private fun buildMetaJson(meta: ZhixuDrawMeta): String {
        val obj =
            JSONObject()
                .put("format", "zhixud")
                .put("formatVersion", meta.formatVersion)
                .put("createdAtMs", meta.createdAtMs)
                .put("modifiedAtMs", meta.modifiedAtMs)
                .put(
                    "pages",
                    JSONArray().apply {
                        for (file in meta.pageOrder) {
                            put(JSONObject().put("file", file))
                        }
                    },
                )
        return obj.toString()
    }

    private fun buildPageJson(page: ZhixuDrawPage): String {
        val elements = JSONArray()
        for (el in page.elements) {
            elements.put(
                when (el) {
                    is ZhixuDrawStroke -> {
                        JSONObject()
                            .put("type", "stroke")
                            .put("id", el.id)
                            .put("tool", el.tool.name.lowercase(Locale.US))
                            .put("colorArgb", el.colorArgb)
                            .put("width", el.width)
                            .put("alpha", el.alpha)
                            .put(
                                "points",
                                JSONArray().apply {
                                    for (p in el.points) {
                                        put(JSONArray().put(p.x).put(p.y))
                                    }
                                },
                            )
                    }

                    is ZhixuDrawShapeElement -> {
                        JSONObject()
                            .put("type", "shape")
                            .put("id", el.id)
                            .put("shape", el.shape.name.lowercase(Locale.US))
                            .put("colorArgb", el.colorArgb)
                            .put("width", el.width)
                            .put("alpha", el.alpha)
                            .put("start", JSONArray().put(el.start.x).put(el.start.y))
                            .put("end", JSONArray().put(el.end.x).put(el.end.y))
                    }
                },
            )
        }

        return JSONObject()
            .put("id", page.id)
            .put("width", page.width)
            .put("height", page.height)
            .put("backgroundColorArgb", page.backgroundColorArgb)
            .put("elements", elements)
            .toString()
    }

    private fun parseMeta(obj: JSONObject): ZhixuDrawMeta {
        val version = obj.optInt("formatVersion", 1).coerceAtLeast(1)
        val created = obj.optLong("createdAtMs", System.currentTimeMillis())
        val modified = obj.optLong("modifiedAtMs", created)
        val pagesArr = obj.optJSONArray("pages") ?: JSONArray()
        val order = ArrayList<String>(pagesArr.length())
        for (i in 0 until pagesArr.length()) {
            val item = pagesArr.optJSONObject(i) ?: continue
            val file = item.optString("file").orEmpty().trim()
            if (file.isNotBlank()) order += file
        }
        return ZhixuDrawMeta(
            formatVersion = version,
            createdAtMs = created,
            modifiedAtMs = modified,
            pageOrder = order,
        )
    }

    private fun parsePage(obj: JSONObject): ZhixuDrawPage {
        val id = obj.optString("id").orEmpty().ifBlank { "page" }
        val width = obj.optDouble("width", 0.0).toFloat().takeIf { it > 1f } ?: 595f
        val height = obj.optDouble("height", 0.0).toFloat().takeIf { it > 1f } ?: 842f
        val backgroundColorArgb = obj.optInt("backgroundColorArgb", 0xFFFFFFFF.toInt())
        val elementsArr = obj.optJSONArray("elements") ?: JSONArray()
        val elements = ArrayList<ZhixuDrawElement>(elementsArr.length())

        for (i in 0 until elementsArr.length()) {
            val elObj = elementsArr.optJSONObject(i) ?: continue
            val type = elObj.optString("type").orEmpty()
            val elId = elObj.optString("id").orEmpty().ifBlank { "el_$i" }
            when (type) {
                "stroke" -> {
                    val toolStr = elObj.optString("tool").orEmpty()
                    val tool =
                        when (toolStr.lowercase(Locale.US)) {
                            "highlighter" -> ZhixuDrawTool.Highlighter
                            "shape" -> ZhixuDrawTool.Shape
                            else -> ZhixuDrawTool.Pen
                        }
                    val color = elObj.optInt("colorArgb", 0xFF000000.toInt())
                    val w = elObj.optDouble("width", 3.0).toFloat().coerceAtLeast(0.2f)
                    val alpha = elObj.optDouble("alpha", 1.0).toFloat().coerceIn(0f, 1f)
                    val ptsArr = elObj.optJSONArray("points") ?: JSONArray()
                    val pts = ArrayList<Offset>(ptsArr.length())
                    for (j in 0 until ptsArr.length()) {
                        val pair = ptsArr.optJSONArray(j) ?: continue
                        if (pair.length() < 2) continue
                        pts += Offset(pair.optDouble(0, 0.0).toFloat(), pair.optDouble(1, 0.0).toFloat())
                    }
                    elements +=
                        ZhixuDrawStroke(
                            id = elId,
                            tool = tool,
                            colorArgb = color,
                            width = w,
                            alpha = alpha,
                            points = pts,
                        )
                }

                "shape" -> {
                    val shapeStr = elObj.optString("shape").orEmpty()
                    val shape =
                        when (shapeStr.lowercase(Locale.US)) {
                            "rectangle" -> ZhixuDrawShape.Rectangle
                            "ellipse" -> ZhixuDrawShape.Ellipse
                            else -> ZhixuDrawShape.Line
                        }
                    val color = elObj.optInt("colorArgb", 0xFF000000.toInt())
                    val w = elObj.optDouble("width", 3.0).toFloat().coerceAtLeast(0.2f)
                    val alpha = elObj.optDouble("alpha", 1.0).toFloat().coerceIn(0f, 1f)
                    val startArr = elObj.optJSONArray("start") ?: JSONArray()
                    val endArr = elObj.optJSONArray("end") ?: JSONArray()
                    val start = Offset(startArr.optDouble(0, 0.0).toFloat(), startArr.optDouble(1, 0.0).toFloat())
                    val end = Offset(endArr.optDouble(0, 0.0).toFloat(), endArr.optDouble(1, 0.0).toFloat())
                    elements +=
                        ZhixuDrawShapeElement(
                            id = elId,
                            shape = shape,
                            colorArgb = color,
                            width = w,
                            alpha = alpha,
                            start = start,
                            end = end,
                        )
                }
            }
        }

        return ZhixuDrawPage(
            id = id,
            width = width,
            height = height,
            backgroundColorArgb = backgroundColorArgb,
            elements = elements,
        )
    }

    private fun decodeLegacyContentXml(xml: String): ZhixuDrawDocument {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        var pageWidth = 595f
        var pageHeight = 842f
        val strokes = ArrayList<ZhixuDrawStroke>()

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "page" -> {
                        pageWidth = parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: pageWidth
                        pageHeight = parser.getAttributeValue(null, "height")?.toFloatOrNull() ?: pageHeight
                    }

                    "stroke" -> {
                        val color = parser.getAttributeValue(null, "color")?.let(::rgbaHexToArgb) ?: 0xFF000000.toInt()
                        val w = parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: 3f
                        val body = parser.nextText().orEmpty()
                        val points = parseStrokePoints(body)
                        strokes +=
                            ZhixuDrawStroke(
                                id = "legacy_${strokes.size}",
                                tool = ZhixuDrawTool.Pen,
                                colorArgb = color,
                                width = w,
                                alpha = 1f,
                                points = points,
                            )
                    }
                }
            }
            parser.next()
        }

        val now = System.currentTimeMillis()
        val page = ZhixuDrawPage(id = "page_001", width = pageWidth, height = pageHeight, backgroundColorArgb = 0xFFFFFFFF.toInt(), elements = strokes)
        val meta =
            ZhixuDrawMeta(
                formatVersion = 1,
                createdAtMs = now,
                modifiedAtMs = now,
                pageOrder = listOf(pageFileName(0)),
            )
        return ZhixuDrawDocument(meta = meta, pages = listOf(page))
    }

    private fun parseStrokePoints(text: String): List<Offset> {
        val items = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (items.isEmpty()) return emptyList()
        val floats = items.mapNotNull { it.toFloatOrNull() }
        val out = ArrayList<Offset>(floats.size / 2)
        var i = 0
        while (i + 1 < floats.size) {
            out.add(Offset(floats[i], floats[i + 1]))
            i += 2
        }
        return out
    }

    private fun rgbaHexToArgb(hex: String): Int {
        val s = hex.trim().removePrefix("#")
        if (s.length == 8) {
            val r = s.substring(0, 2).toInt(16)
            val g = s.substring(2, 4).toInt(16)
            val b = s.substring(4, 6).toInt(16)
            val a = s.substring(6, 8).toInt(16)
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        if (s.length == 6) {
            val r = s.substring(0, 2).toInt(16)
            val g = s.substring(2, 4).toInt(16)
            val b = s.substring(4, 6).toInt(16)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return 0xFF000000.toInt()
    }
}
