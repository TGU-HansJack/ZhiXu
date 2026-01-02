package com.zhixu.android.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.data.WebDavClient
import com.zhixu.android.data.WebDavConfig
import com.zhixu.android.data.vaultRootToDocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class WebDavSyncSummary(
    val uploaded: Int,
    val downloaded: Int,
    val conflicts: Int,
    val failed: Int,
)

internal data class RemoteEntry(
    val path: String,
    val isDir: Boolean,
    val size: Long?,
    val lastModifiedEpochMs: Long?,
)

internal data class LocalEntry(
    val path: String,
    val file: DocumentFile,
    val size: Long,
    val lastModifiedEpochMs: Long,
)

class WebDavSyncEngine(
    private val context: Context,
    private val repository: VaultRepository,
) {
    suspend fun syncVault(rootUri: Uri, config: WebDavConfig): WebDavSyncSummary = withContext(Dispatchers.IO) {
        require(config.enabled) { "WebDAV is disabled" }
        repository.ensureVaultStructure(rootUri)
        if (config.includeIndexSqlite) {
            repository.exportIndexSqlite(rootUri)
        }

        val root = vaultRootToDocumentFile(context, rootUri) ?: error("Invalid vault root Uri")
        val logFile = ensureLocalFile(root, ".zhixu/sync/log.jsonl")
        val conflictsFile = ensureLocalFile(root, ".zhixu/sync/conflicts.jsonl")
        val stateFile = ensureLocalFile(root, ".zhixu/sync/webdav_state.json")
        val logger = SyncLogger(logFile?.uri, conflictsFile?.uri)
        logger.logEvent("start", mapOf("includeIndexSqlite" to config.includeIndexSqlite.toString()))

        runCatching {
            val prevPaths = readStatePaths(stateFile?.uri)
            val localFiles = listLocalFiles(root, includeIndexSqlite = config.includeIndexSqlite).filter { shouldSyncPath(it.path) }
            val remoteRootUrl =
                WebDavClient.normalizeJoin(config.baseUrl.trim(), config.remoteRoot.trim().ifBlank { "/" }).trimEnd('/') + "/"
            logger.logEvent("remote_root", mapOf("url" to remoteRootUrl))

            ensureRemoteRoot(remoteRootUrl, config)
            val ensuredRemoteDirs = HashSet<String>()
            val remoteFiles = listRemoteFiles(remoteRootUrl, config)

            var uploaded = 0
            var downloaded = 0
            var conflicts = 0
            var failed = 0

            val remoteByPath = remoteFiles.filter { !it.isDir }.filter { shouldSyncPath(it.path) }.associateBy { it.path }
            val localByPath = localFiles.associateBy { it.path }

            // Remote-only: download new files, or propagate local deletions.
            for ((path, _) in remoteByPath) {
                if (localByPath.containsKey(path)) continue
                val ok =
                    if (prevPaths.contains(path)) {
                        // It existed last sync but now missing locally -> treat as local deletion.
                        try {
                            val code = WebDavClient.delete(config, buildRemoteUrl(remoteRootUrl, path))
                            code in 200..299 || code == 404
                        } catch (e: Throwable) {
                            logger.fileFailed("delete_remote", path, e)
                            false
                        }
                    } else {
                        try {
                            downloadFile(root, remoteRootUrl, path, config)
                        } catch (e: Throwable) {
                            logger.fileFailed("download", path, e)
                            false
                        }
                    }
                if (ok) downloaded++ else failed++
            }

            // Upload local-only files and handle conflicts.
            for (local in localFiles) {
                val remote = remoteByPath[local.path]
                if (remote == null) {
                    val ok =
                        try {
                            uploadFile(remoteRootUrl, local, config, ensuredRemoteDirs)
                        } catch (e: Throwable) {
                            logger.fileFailed("upload", local.path, e)
                            false
                        }
                    if (ok) uploaded++ else failed++
                    continue
                }

                val same =
                    remote.size != null &&
                        remote.lastModifiedEpochMs != null &&
                        remote.size == local.size &&
                        kotlin.math.abs(remote.lastModifiedEpochMs - local.lastModifiedEpochMs) < 2_000

                if (same) continue

                // Conflict: keep local, snapshot remote under .zhixu/conflicts/, then overwrite remote with local.
                val conflictResult =
                    try {
                        downloadConflictFile(root, remoteRootUrl, local.path, config)
                    } catch (e: Throwable) {
                        logger.fileFailed("download_conflict", local.path, e)
                        ConflictDownloadResult(false, null)
                    }
                if (conflictResult.ok) {
                    conflicts++
                    logger.conflict(local.path, conflictResult.conflictPath ?: "")
                } else {
                    failed++
                    continue
                }

                val okOverwrite =
                    try {
                        uploadFile(remoteRootUrl, local, config, ensuredRemoteDirs)
                    } catch (e: Throwable) {
                        logger.fileFailed("upload_conflict_overwrite", local.path, e)
                        false
                    }
                if (okOverwrite) uploaded++ else failed++
            }

            if (!config.includeIndexSqlite) {
                runCatching { repository.rebuildIndex(rootUri) }
            }

            runCatching {
                val currentPaths = listLocalFiles(root, includeIndexSqlite = config.includeIndexSqlite).map { it.path }.filter { shouldSyncPath(it) }.sorted()
                writeStatePaths(stateFile?.uri, currentPaths)
            }

            WebDavSyncSummary(
                uploaded = uploaded,
                downloaded = downloaded,
                conflicts = conflicts,
                failed = failed,
            )
        }.fold(
            onSuccess = { summary ->
                logger.logEvent(
                    "end",
                    mapOf(
                        "ok" to "true",
                        "uploaded" to summary.uploaded.toString(),
                        "downloaded" to summary.downloaded.toString(),
                        "conflicts" to summary.conflicts.toString(),
                        "failed" to summary.failed.toString(),
                    ),
                )
                summary
            },
            onFailure = { e ->
                logger.logEvent("end", mapOf("ok" to "false", "error" to (e.message ?: e.javaClass.simpleName)))
                throw e
            },
        )
    }

    private data class ConflictDownloadResult(val ok: Boolean, val conflictPath: String?)

    private inner class SyncLogger(
        private val logUri: Uri?,
        private val conflictsUri: Uri?,
    ) {
        suspend fun logEvent(event: String, fields: Map<String, String>) {
            val uri = logUri ?: return
            val now = System.currentTimeMillis()
            val payload = LinkedHashMap<String, String>(fields.size + 2)
            payload["ts"] = now.toString()
            payload["event"] = event
            payload.putAll(fields)
            val line = payload.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
            }
            runCatching { repository.appendText(uri, "$line\n") }
        }

        suspend fun fileFailed(op: String, path: String, throwable: Throwable) {
            logEvent(
                "file_failed",
                mapOf(
                    "op" to op,
                    "path" to path,
                    "error" to (throwable.message ?: throwable.javaClass.simpleName),
                ),
            )
        }

        suspend fun conflict(path: String, conflictPath: String) {
            val uri = conflictsUri ?: return
            val now = System.currentTimeMillis()
            val line =
                "{" +
                    "\"ts\":\"$now\"," +
                    "\"path\":\"${escapeJson(path)}\"," +
                    "\"conflictPath\":\"${escapeJson(conflictPath)}\"" +
                    "}"
            runCatching { repository.appendText(uri, "$line\n") }
        }
    }

    private fun escapeJson(raw: String): String {
        val out = StringBuilder(raw.length + 16)
        for (ch in raw) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun listLocalFiles(root: DocumentFile, includeIndexSqlite: Boolean): List<LocalEntry> {
        val out = ArrayList<LocalEntry>()

        fun walk(dir: DocumentFile, prefix: String) {
            for (child in dir.listFiles()) {
                val name = child.name ?: continue
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                if (child.isDirectory) {
                    if (path.equals(".zhixu/sync", ignoreCase = true)) continue
                    if (path.startsWith(".zhixu/sync/", ignoreCase = true)) continue
                    if (path.equals(".zhixu/conflicts", ignoreCase = true)) continue
                    if (path.startsWith(".zhixu/conflicts/", ignoreCase = true)) continue
                    if (path.equals(".zhixu/history", ignoreCase = true)) continue
                    if (path.startsWith(".zhixu/history/", ignoreCase = true)) continue
                    walk(child, path)
                } else if (child.isFile) {
                    if (!includeIndexSqlite && path.equals(".zhixu/index.sqlite", ignoreCase = true)) continue
                    out += LocalEntry(
                        path = path,
                        file = child,
                        size = child.length(),
                        lastModifiedEpochMs = child.lastModified(),
                    )
                }
            }
        }

        walk(root, "")

        return out
    }

    private fun shouldSyncPath(path: String): Boolean {
        val p = path.trimStart('/')
        val name = p.substringAfterLast('/', missingDelimiterValue = p)
        if (name.startsWith("conflict ", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/sync/", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/conflicts/", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/history/", ignoreCase = true)) return false
        return true
    }

    private suspend fun ensureRemoteRoot(remoteRootUrl: String, config: WebDavConfig) {
        // Best-effort: attempt MKCOL on the root; ignore common "already exists" codes.
        val code = WebDavClient.mkcol(config, remoteRootUrl)
        if (code == 201 || code == 405 || code == 409) return
        // Some servers require creating parents; try progressively.
        val url = remoteRootUrl.trimEnd('/')
        val idx = url.indexOf("://")
        val pathStart = if (idx >= 0) url.indexOf('/', idx + 3) else -1
        if (pathStart < 0) return
        val base = url.substring(0, pathStart)
        val path = url.substring(pathStart).trim('/')
        var current = base
        for (segment in path.split('/').filter { it.isNotBlank() }) {
            current += "/$segment"
            val c = WebDavClient.mkcol(config, "$current/")
            if (c !in listOf(201, 405)) {
                // ignore
            }
        }
    }

    private suspend fun ensureRemoteDirs(
        remoteRootUrl: String,
        relativeDirPath: String,
        config: WebDavConfig,
        cache: MutableSet<String>,
    ) {
        val parts = relativeDirPath.trim('/').split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        var current = ""
        for (part in parts) {
            current = if (current.isBlank()) part else "$current/$part"
            if (!cache.add(current)) continue
            val url = buildRemoteUrl(remoteRootUrl, current).ensureTrailingSlash()
            val code = WebDavClient.mkcol(config, url)
            // 201 Created, 405 Method Not Allowed (already exists), 409 Conflict (some servers use this when exists or parent missing).
            if (code !in listOf(201, 405, 409)) {
                // Ignore; a later PUT/GET will surface the real failure.
            }
        }
    }

    private suspend fun listRemoteFiles(remoteRootUrl: String, config: WebDavConfig): List<RemoteEntry> {
        val rootPath = Uri.parse(remoteRootUrl).path ?: "/"
        val toVisit = ArrayDeque<String>()
        toVisit.add(remoteRootUrl)
        val out = ArrayList<RemoteEntry>()

        while (toVisit.isNotEmpty()) {
            val dirUrl = toVisit.removeFirst()
            val (code, xml) = WebDavClient.propfind(config, dirUrl, depth = "1")
            if (code != 207 && code !in 200..299) continue
            val entries = parsePropfind(xml)
            for (entry in entries) {
                val href = entry.path
                val normalizedPath = normalizeHrefToPath(href, rootPath)
                if (normalizedPath.isEmpty()) continue
                val remote = entry.copy(path = normalizedPath)
                out += remote
                if (remote.isDir) {
                    toVisit.add(remoteRootUrl + normalizedPath.trimEnd('/') + "/")
                }
            }
        }

        return out.distinctBy { it.path }
    }

    private fun normalizeHrefToPath(href: String, rootPath: String): String {
        val decoded = Uri.decode(href)
        val path = Uri.parse(decoded).path ?: decoded
        val trimmedRoot = rootPath.trimEnd('/')
        val trimmedPath = path.trimEnd('/')
        val rel =
            when {
                trimmedPath == trimmedRoot -> ""
                trimmedPath.startsWith("$trimmedRoot/") -> trimmedPath.removePrefix("$trimmedRoot/").trimStart('/')
                else -> trimmedPath.trimStart('/')
            }
        return rel
    }

    private fun parsePropfind(xml: String): List<RemoteEntry> {
        val trimmed = xml.trimStart()
        if (trimmed.isBlank() || !trimmed.startsWith("<")) return emptyList()

        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())

        val out = ArrayList<RemoteEntry>()
        var href: String? = null
        var isDir: Boolean? = null
        var size: Long? = null
        var lastModified: Long? = null

        fun flush() {
            val h = href ?: return
            val d = isDir ?: false
            out += RemoteEntry(path = h, isDir = d, size = size, lastModifiedEpochMs = lastModified)
            href = null
            isDir = null
            size = null
            lastModified = null
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name.lowercase()) {
                        "response" -> {
                            href = null; isDir = null; size = null; lastModified = null
                        }

                        "href" -> href = parser.nextText()
                        "getcontentlength" -> size = parser.nextText().trim().toLongOrNull()
                        "getlastmodified" -> lastModified = parseHttpDate(parser.nextText())
                        "collection" -> isDir = true
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name.lowercase() == "response") flush()
                }
            }
            event = parser.next()
        }

        return out
    }

    private fun parseHttpDate(text: String): Long? {
        return runCatching {
            ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private suspend fun uploadFile(
        remoteRootUrl: String,
        local: LocalEntry,
        config: WebDavConfig,
        ensuredRemoteDirs: MutableSet<String>,
    ): Boolean {
        val parentPath = local.path.substringBeforeLast('/', missingDelimiterValue = "").trim()
        if (parentPath.isNotBlank()) {
            ensureRemoteDirs(remoteRootUrl, parentPath, config, ensuredRemoteDirs)
        }
        val url = buildRemoteUrl(remoteRootUrl, local.path)
        val bytes = repository.readBytes(local.file.uri) ?: return false
        val code = WebDavClient.put(config, url, local.file.type, bytes)
        return code in 200..299 || code == 204
    }

    private suspend fun downloadFile(root: DocumentFile, remoteRootUrl: String, path: String, config: WebDavConfig): Boolean {
        val url = buildRemoteUrl(remoteRootUrl, path)
        val (code, bytes) = WebDavClient.get(config, url)
        if (code !in 200..299) return false
        val dest = ensureLocalFile(root, path) ?: return false
        repository.writeBytes(dest.uri, bytes)
        return true
    }

    private suspend fun downloadConflictFile(
        root: DocumentFile,
        remoteRootUrl: String,
        path: String,
        config: WebDavConfig,
    ): ConflictDownloadResult {
        val url = buildRemoteUrl(remoteRootUrl, path)
        val (code, bytes) = WebDavClient.get(config, url)
        if (code !in 200..299) return ConflictDownloadResult(false, null)
        val safe = path.trimStart('/').replace('\\', '/')
        val destPath = ".zhixu/conflicts/$safe/${System.currentTimeMillis()}-remote"
        val dest = ensureLocalFile(root, destPath) ?: return ConflictDownloadResult(false, null)
        repository.writeBytes(dest.uri, bytes)
        return ConflictDownloadResult(true, destPath)
    }

    private suspend fun readStatePaths(stateUri: Uri?): Set<String> {
        val uri = stateUri ?: return emptySet()
        val text = runCatching { repository.readText(uri) }.getOrNull().orEmpty()
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return emptySet()
        val arr = obj.optJSONArray("paths") ?: JSONArray()
        val out = HashSet<String>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optString(i).orEmpty()
            if (p.isNotBlank()) out += p
        }
        return out
    }

    private suspend fun writeStatePaths(stateUri: Uri?, paths: List<String>) {
        val uri = stateUri ?: return
        val obj =
            JSONObject()
                .put("version", 1)
                .put("savedAt", System.currentTimeMillis())
                .put("paths", JSONArray(paths))
        runCatching { repository.writeText(uri, obj.toString()) }
    }

    private fun buildRemoteUrl(remoteRootUrl: String, relativePath: String): String {
        val base = remoteRootUrl.toHttpUrl()
        return base.newBuilder()
            .addPathSegments(relativePath.trimStart('/'))
            .build()
            .toString()
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

    private fun ensureLocalFile(root: DocumentFile, path: String): DocumentFile? {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        if (root.uri.scheme.equals("file", ignoreCase = true)) {
            val base = root.uri.path?.let { java.io.File(it) } ?: return null
            var dir = base
            for (i in 0 until parts.size - 1) {
                dir = java.io.File(dir, parts[i])
            }
            dir.mkdirs()
            val file = java.io.File(dir, parts.last())
            runCatching { if (!file.exists()) file.createNewFile() }
            return DocumentFile.fromFile(file)
        }
        var dir = root
        for (i in 0 until parts.size - 1) {
            val name = parts[i]
            dir = dir.findFile(name) ?: dir.createDirectory(name) ?: return null
        }
        val fileName = parts.last()
        return dir.findFile(fileName) ?: dir.createFile("application/octet-stream", fileName)
    }
}
