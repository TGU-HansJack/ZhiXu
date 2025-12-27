package com.zhixu.android.plugins

data class FrontMatter(
    val raw: String,
    val body: String,
    val map: Map<String, Any?>,
) {
    fun string(key: String): String? = map[key]?.toString()?.trim()?.takeIf { it.isNotBlank() }

    fun bool(key: String): Boolean? =
        when (val v = map[key]) {
            is Boolean -> v
            is String ->
                when (v.trim().lowercase()) {
                    "true", "yes", "y", "1" -> true
                    "false", "no", "n", "0" -> false
                    else -> null
                }
            is Number -> v.toInt() != 0
            else -> null
        }

    fun stringList(key: String): List<String> {
        val v = map[key]
        return when (v) {
            is List<*> -> v.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
            is String -> v.split(',').map { it.trim() }.filter { it.isNotBlank() }
            else -> emptyList()
        }
    }
}

object FrontMatterParser {
    fun parse(markdown: String): FrontMatter {
        val text = markdown
        val trimmedStart = text.trimStart()
        if (!trimmedStart.startsWith("---\n") && !trimmedStart.startsWith("---\r\n")) {
            return FrontMatter(raw = "", body = markdown, map = emptyMap())
        }

        val startIdx = text.indexOf("---")
        if (startIdx < 0) return FrontMatter(raw = "", body = markdown, map = emptyMap())

        val afterStart = text.indexOf('\n', startIdx).let { if (it < 0) return FrontMatter("", markdown, emptyMap()) else it + 1 }
        val endMarkerIdx = findEndMarker(text, afterStart) ?: return FrontMatter(raw = "", body = markdown, map = emptyMap())
        val front = text.substring(afterStart, endMarkerIdx)
        val afterEndLine = text.indexOf('\n', endMarkerIdx).let { if (it < 0) text.length else it + 1 }
        val body = text.substring(afterEndLine)
        val map = parseYamlLike(front)
        return FrontMatter(raw = front, body = body, map = map)
    }

    fun upsert(markdown: String, updates: Map<String, Any?>): String {
        val parsed = parse(markdown)
        val existing = parsed.map.toMutableMap()
        for ((k, v) in updates) {
            if (v == null) {
                existing.remove(k)
            } else {
                existing[k] = v
            }
        }
        val yaml = renderYaml(existing)
        return buildString {
            append("---\n")
            append(yaml)
            append("---\n")
            append(parsed.body.trimStart('\n', '\r'))
        }
    }

    private fun findEndMarker(text: String, from: Int): Int? {
        var idx = from
        while (idx < text.length) {
            val lineEnd = text.indexOf('\n', idx).let { if (it < 0) text.length else it }
            val line = text.substring(idx, lineEnd).trim()
            if (line == "---" || line == "...") return idx
            idx = lineEnd + 1
        }
        return null
    }

    private fun parseYamlLike(front: String): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        val lines = front.split('\n')
        var i = 0
        while (i < lines.size) {
            val raw = lines[i].trimEnd('\r')
            val line = raw.trim()
            i++
            if (line.isBlank() || line.startsWith("#")) continue

            // list item continuation: key:
            if (line.endsWith(":") && !line.contains(": ")) {
                val key = line.removeSuffix(":").trim()
                val items = ArrayList<String>()
                while (i < lines.size) {
                    val next = lines[i].trimEnd('\r')
                    val t = next.trim()
                    if (!t.startsWith("- ")) break
                    items += t.removePrefix("- ").trim().trim('"')
                    i++
                }
                map[key] = items
                continue
            }

            val sep = line.indexOf(':')
            if (sep <= 0) continue
            val key = line.substring(0, sep).trim()
            var value = line.substring(sep + 1).trim()
            if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
                value = value.substring(1, value.length - 1)
            }
            if (value.startsWith("[") && value.endsWith("]")) {
                val inner = value.substring(1, value.length - 1)
                val parts = inner.split(',').map { it.trim().trim('"') }.filter { it.isNotBlank() }
                map[key] = parts
                continue
            }
            val bool =
                when (value.lowercase()) {
                    "true", "yes", "y", "1" -> true
                    "false", "no", "n", "0" -> false
                    else -> null
                }
            map[key] = bool ?: value
        }
        return map
    }

    private fun renderYaml(map: Map<String, Any?>): String {
        if (map.isEmpty()) return ""
        val keys = map.keys.sorted()
        return buildString {
            for (k in keys) {
                val v = map[k]
                when (v) {
                    null -> Unit
                    is Boolean -> append("$k: ${if (v) "true" else "false"}\n")
                    is Number -> append("$k: $v\n")
                    is List<*> -> {
                        append("$k:\n")
                        for (item in v) {
                            val s = item?.toString()?.trim().orEmpty()
                            if (s.isNotBlank()) append("- $s\n")
                        }
                    }
                    else -> append("$k: ${v.toString().replace("\n", " ").trim()}\n")
                }
            }
        }
    }
}

