package app.zhixu.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppLogRepository(
    private val appContext: Context,
) {
    enum class Kind(
        val fileName: String,
    ) {
        Operation("operation.log"),
        Plugin("plugin.log"),
        Ai("ai.log"),
    }

    private fun ensureDir(): File {
        val dir = File(appContext.filesDir, "zhixu-logs")
        if (!dir.exists()) runCatching { dir.mkdirs() }
        return dir
    }

    private fun file(kind: Kind): File = File(ensureDir(), kind.fileName)

    fun appendBlocking(kind: Kind, message: String) {
        val line = "${now()} $message\n"
        runCatching { file(kind).appendText(line, Charsets.UTF_8) }
    }

    fun readBlocking(kind: Kind, maxLines: Int = 800): String {
        val f = file(kind)
        if (!f.exists() || !f.isFile) return ""
        val raw = runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("")
        if (raw.isBlank()) return ""
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.size <= maxLines) return lines.joinToString("\n")
        return lines.takeLast(maxLines).joinToString("\n")
    }

    fun clearBlocking(kind: Kind) {
        runCatching { file(kind).delete() }
    }

    private fun now(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return "[${fmt.format(Date())}]"
    }
}

