package app.zhixu.draw

import androidx.compose.ui.geometry.Offset
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

object ZhixuDrawFormat {
    const val MIME_TYPE: String = "application/zhixu-drawing"
    const val EXTENSION: String = ".zhixu"

    fun hasDrawingExtension(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower.endsWith(EXTENSION) && lower.length > EXTENSION.length
    }

    fun stripDrawingExtension(name: String): String {
        val lower = name.lowercase(Locale.US)
        return when {
            lower.endsWith(EXTENSION) && lower.length > EXTENSION.length -> name.dropLast(EXTENSION.length)
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
        error("Invalid drawing file: missing meta.json")
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
                                        val pt =
                                            JSONArray()
                                                .put(p.x)
                                                .put(p.y)
                                        when (p) {
                                            is ZhixuDrawRoundPoint -> pt.put(p.width).put(p.alpha)
                                            is ZhixuDrawFlatPoint -> pt.put(p.rx).put(p.ry).put(p.angle).put(p.alpha)
                                            else -> Unit
                                        }
                                        put(pt)
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
                    val pts = ArrayList<ZhixuDrawStrokePoint>(ptsArr.length())
                    for (j in 0 until ptsArr.length()) {
                        val pair = ptsArr.optJSONArray(j) ?: continue
                        if (pair.length() < 2) continue
                        val x = pair.optDouble(0, 0.0).toFloat()
                        val y = pair.optDouble(1, 0.0).toFloat()
                        pts +=
                            when {
                                pair.length() >= 6 ->
                                    ZhixuDrawFlatPoint(
                                        x = x,
                                        y = y,
                                        rx = pair.optDouble(2, 0.0).toFloat(),
                                        ry = pair.optDouble(3, 0.0).toFloat(),
                                        angle = pair.optDouble(4, 0.0).toFloat(),
                                        alpha = pair.optDouble(5, 1.0).toFloat().coerceIn(0f, 1f),
                                    )
                                pair.length() >= 4 ->
                                    ZhixuDrawRoundPoint(
                                        x = x,
                                        y = y,
                                        width = pair.optDouble(2, w.toDouble()).toFloat().coerceAtLeast(0.2f),
                                        alpha = pair.optDouble(3, alpha.toDouble()).toFloat().coerceIn(0f, 1f),
                                    )
                                else -> ZhixuDrawBasicPoint(x = x, y = y)
                            }
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
}
