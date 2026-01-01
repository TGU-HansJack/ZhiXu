package com.zhixu.android.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.os.SystemClock
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.zhixu.core.tasks.TaskSyntax
import com.zhixu.core.tasks.Ulid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class UiDoc(
    val name: String,
    val uri: Uri,
    val lastModified: Long,
    val size: Long,
    val baseName: String = "",
    val createdAt: Long = 0L,
    val createdAtText: String = "",
    val editedAtText: String = "",
)

data class VaultTreeEntry(
    val relativePath: String,
    val name: String,
    val uri: Uri?,
    val isDirectory: Boolean,
    val parentPath: String?,
    val depth: Int,
)

data class TaskStats(
    val done: Int,
    val total: Int,
    val donePerDay: Map<LocalDate, Int>,
)

class VaultRepository(
    private val context: Context,
) {
    private val appConfigDirName = ".zhixu"
    private val indexRepository = VaultIndexRepository(context)
    private val dirIndexRepository = VaultDirIndexRepository(context)
    private val dueFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    @Volatile private var indexBuildInProgress: Boolean = false
    @Volatile private var dirIndexBuildInProgress: Boolean = false
    private val dirIndexBuildLock = Mutex()

    @Volatile private var indexChangeVersion: Long = 0L
    private val indexChangesInternal = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 8)
    val indexChanges: SharedFlow<Long> = indexChangesInternal.asSharedFlow()

    fun isIndexBuildInProgress(): Boolean = indexBuildInProgress
    fun isDirIndexBuildInProgress(): Boolean = dirIndexBuildInProgress

    private fun signalIndexChanged() {
        indexChangeVersion += 1L
        indexChangesInternal.tryEmit(indexChangeVersion)
    }

    private data class DocListCacheEntry(
        val docs: List<UiDoc>,
        val listedAtUptimeMs: Long,
        val listedAtWallMs: Long,
        val isDirty: Boolean,
        val docsDirUri: Uri?,
    )

    private val docListCacheLock = Mutex()
    private val docListCacheMaxEntries = 4
    private val docListCache = LinkedHashMap<String, DocListCacheEntry>(docListCacheMaxEntries, 0.75f, true)

    fun invalidateDocListCache(rootUri: Uri) {
        val key = rootUri.toString()
        synchronized(docListCache) {
            val existing = docListCache[key] ?: return
            docListCache[key] = existing.copy(isDirty = true)
        }
    }

    suspend fun setDocListCache(
        rootUri: Uri,
        docs: List<UiDoc>,
        isDirty: Boolean = false,
    ) {
        val key = rootUri.toString()
        val nowUptime = SystemClock.uptimeMillis()
        val nowWall = System.currentTimeMillis()
        docListCacheLock.withLock {
            val prev = synchronized(docListCache) { docListCache[key] }
            val docsDirUri = prev?.docsDirUri ?: runCatching { vaultRootToDocumentFile(context, rootUri)?.uri }.getOrNull()
            val entry =
                DocListCacheEntry(
                    docs = docs,
                    listedAtUptimeMs = nowUptime,
                    listedAtWallMs = nowWall,
                    isDirty = isDirty,
                    docsDirUri = docsDirUri,
                )
            synchronized(docListCache) {
                docListCache[key] = entry
                while (docListCache.size > docListCacheMaxEntries) {
                    val eldestKey = docListCache.entries.firstOrNull()?.key ?: break
                    docListCache.remove(eldestKey)
                }
            }
        }
    }

    fun computeRelativePath(rootUri: Uri, docUri: Uri): String? {
        if (rootUri.toString().isBlank() || docUri.toString().isBlank()) return null

        if (rootUri.scheme.equals("file", ignoreCase = true) && docUri.scheme.equals("file", ignoreCase = true)) {
            val rootPath = rootUri.path ?: return null
            val docPath = docUri.path ?: return null
            val root =
                runCatching { File(rootPath).canonicalFile }
                    .getOrDefault(File(rootPath).absoluteFile)
            val doc =
                runCatching { File(docPath).canonicalFile }
                    .getOrDefault(File(docPath).absoluteFile)
            val rel = runCatching { doc.relativeTo(root).invariantSeparatorsPath }.getOrNull() ?: return null
            return rel.trimStart('/').takeIf { it.isNotBlank() }
        }

        if (rootUri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true) &&
            docUri.scheme.equals(ContentResolver.SCHEME_CONTENT, ignoreCase = true)
        ) {
            val rootId =
                runCatching {
                    if (DocumentsContract.isTreeUri(rootUri)) DocumentsContract.getTreeDocumentId(rootUri) else DocumentsContract.getDocumentId(rootUri)
                }.getOrNull() ?: return null
            val docId = runCatching { DocumentsContract.getDocumentId(docUri) }.getOrNull() ?: return null

            val normalizedRoot = rootId.trimEnd('/')
            val normalizedDoc = docId.trimEnd('/')
            if (normalizedDoc == normalizedRoot) return ""

            val rel =
                when {
                    normalizedDoc.startsWith("$normalizedRoot/") -> normalizedDoc.removePrefix("$normalizedRoot/").trimStart('/')
                    else -> null
                } ?: return null

            return rel.replace('\\', '/').trimStart('/').takeIf { it.isNotBlank() }
        }

        return null
    }

    suspend fun getDocsDirUri(rootUri: Uri): Uri? =
        docListCacheLock.withLock {
            val key = rootUri.toString()
            val cached = synchronized(docListCache) { docListCache[key]?.docsDirUri }
            if (cached != null) return@withLock cached

            val root = vaultRootToDocumentFile(context, rootUri) ?: return@withLock null
            val uri = root.uri
            synchronized(docListCache) {
                val prev = docListCache[key]
                docListCache[key] =
                    (prev ?: DocListCacheEntry(emptyList(), 0L, 0L, isDirty = true, docsDirUri = uri))
                        .copy(docsDirUri = uri)
            }
            uri
        }

    suspend fun listMarkdownDocsCached(
        rootUri: Uri,
        force: Boolean,
        maxStaleMs: Long = 20_000L,
    ): List<UiDoc> {
        val key = rootUri.toString()
        val nowUptime = SystemClock.uptimeMillis()
        val nowWall = System.currentTimeMillis()

        val cached =
            synchronized(docListCache) { docListCache[key] }
        if (!force && cached != null && !cached.isDirty) {
            val ageMs = nowWall - cached.listedAtWallMs
            if (ageMs in 0..maxStaleMs) return cached.docs
        }

        return docListCacheLock.withLock {
            val current = synchronized(docListCache) { docListCache[key] }
            if (!force && current != null && !current.isDirty) {
                val ageMs = nowWall - current.listedAtWallMs
                if (ageMs in 0..maxStaleMs) return@withLock current.docs
            }

            val listed = listMarkdownDocs(rootUri)
            val docsDirUri = runCatching { vaultRootToDocumentFile(context, rootUri)?.uri }.getOrNull()

            val entry =
                DocListCacheEntry(
                    docs = listed,
                    listedAtUptimeMs = nowUptime,
                    listedAtWallMs = nowWall,
                    isDirty = false,
                    docsDirUri = docsDirUri,
                )
            synchronized(docListCache) {
                docListCache[key] = entry
                while (docListCache.size > docListCacheMaxEntries) {
                    val eldestKey = docListCache.entries.firstOrNull()?.key ?: break
                    docListCache.remove(eldestKey)
                }
            }
            listed
        }
    }

    suspend fun getDocumentLastModified(uri: Uri): Long =
        withContext(Dispatchers.IO) {
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val path = uri.path ?: return@withContext 0L
                return@withContext runCatching { File(path).lastModified() }.getOrDefault(0L)
            }
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@runCatching 0L
                    val idx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (idx < 0 || cursor.isNull(idx)) 0L else cursor.getLong(idx)
                } ?: 0L
            }.getOrDefault(0L)
        }

    suspend fun statDocUi(docUri: Uri): UiDoc? =
        withContext(Dispatchers.IO) {
            val name: String?
            val lastModified: Long
            val size: Long

            if (docUri.scheme.equals("file", ignoreCase = true)) {
                val path = docUri.path ?: return@withContext null
                val file = File(path)
                if (!file.exists()) return@withContext null
                name = file.name
                lastModified = file.lastModified()
                size = file.length()
            } else {
                val stat = DocumentFile.fromSingleUri(context, docUri) ?: return@withContext null
                name = stat.name
                lastModified = stat.lastModified()
                size = stat.length()
            }

            val uriStr = docUri.toString()
            val createdAt =
                runCatching { indexRepository.getDocCreatedAtMap(listOf(uriStr))[uriStr] }
                    .getOrNull()
                    ?: lastModified
            val safeName = name ?: "Document.md"
            UiDoc(
                name = safeName,
                uri = docUri,
                lastModified = lastModified,
                size = size,
                baseName = computeDocBaseName(safeName),
                createdAt = createdAt,
                createdAtText = if (createdAt > 0L) formatEditedAt(createdAt) else "",
                editedAtText = if (lastModified > 0L) formatEditedAt(lastModified) else "",
            )
        }

    suspend fun hasAnyIndexedDocs(): Boolean =
        runCatching { indexRepository.hasAnyIndexedDocs() }
            .getOrElse {
                Log.e("Zhixu", "hasAnyIndexedDocs failed", it)
                false
            }

    suspend fun ensureIndexBuilt(rootUri: Uri) {
        if (hasAnyIndexedDocs()) return
        rebuildIndex(rootUri)
    }

    suspend fun listIndexedDocs(
        rootUri: Uri,
        limit: Int = 2000,
    ): List<UiDoc> = withContext(Dispatchers.IO) {
        if (rootUri.toString().isBlank()) return@withContext emptyList()
        val raw = runCatching { indexRepository.listDocs(limit = limit) }.getOrElse { emptyList() }
        raw.map { doc ->
            val created = doc.createdAt.takeIf { it > 0L } ?: doc.lastModified
            doc.copy(
                createdAt = created,
                createdAtText = if (created > 0L) formatEditedAt(created) else "",
                editedAtText = if (doc.lastModified > 0L) formatEditedAt(doc.lastModified) else "",
            )
        }
    }

    suspend fun ensureVaultStructure(rootUri: Uri) = withContext(Dispatchers.IO) {
        val root = vaultRootToDocumentFile(context, rootUri) ?: error("Invalid vault root Uri")
        val zhixu = findChild(root, appConfigDirName) ?: root.createDirectory(appConfigDirName)
        val syncDir = zhixu?.let { findChild(it, "sync") ?: it.createDirectory("sync") }
        val exportsDir = zhixu?.let { findChild(it, "exports") ?: it.createDirectory("exports") }
        val pluginsDir = zhixu?.let { findChild(it, "plugins") ?: it.createDirectory("plugins") }
        val pluginState =
            pluginsDir?.let {
                findChild(it, "state.json") ?: createFileExact(it, "application/json", "state.json")
            }
        val settings = zhixu?.let { findChild(it, "settings.json") ?: createFileExact(it, "application/json", "settings.json") }
        if (settings != null && settings.length() == 0L) {
            writeText(settings.uri, "{}\n")
        }
        if (pluginState != null && pluginState.length() == 0L) {
            writeText(pluginState.uri, "{\n  \"enabled\": []\n}\n")
        }

        // Touch to keep variables used (and validate creation).
        requireNotNull(zhixu) { ".zhixu/ directory missing" }
        // Validate creation of subdirectories too.
        requireNotNull(syncDir) { ".zhixu/sync directory missing" }
        requireNotNull(exportsDir) { ".zhixu/exports directory missing" }
        requireNotNull(pluginsDir) { ".zhixu/plugins directory missing" }
    }

    suspend fun computeVaultTotalSizeBytes(rootUri: Uri): Long = withContext(Dispatchers.IO) {
        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext 0L

        var total = 0L
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)

        var visitedDirs = 0
        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            for (child in dir.listFiles()) {
                if (child.isDirectory) {
                    stack.add(child)
                } else if (child.isFile) {
                    total += child.length()
                }
            }
            visitedDirs += 1
            if (visitedDirs % 20 == 0) yield()
        }

        total
    }

    suspend fun resolveVaultFileUri(rootUri: Uri, relativePath: String): Uri? = withContext(Dispatchers.IO) {
        val rel = relativePath.replace('\\', '/').trimStart('/').trim()
        if (rel.isBlank()) return@withContext null

        if (rootUri.scheme.equals("file", ignoreCase = true)) {
            val rootPath = rootUri.path ?: return@withContext null
            val file = File(rootPath, rel)
            if (!file.exists()) return@withContext null
            return@withContext Uri.fromFile(file)
        }

        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext null
        var current: DocumentFile = root
        val parts = rel.split('/').filter { it.isNotBlank() }
        for (i in parts.indices) {
            val name = parts[i]
            val next = current.findFile(name) ?: return@withContext null
            if (i < parts.lastIndex && !next.isDirectory) return@withContext null
            current = next
        }
        current.uri
    }

    suspend fun listMarkdownDocs(rootUri: Uri): List<UiDoc> = withContext(Dispatchers.IO) {
        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext emptyList()
        val docs = ArrayList<UiDoc>()

        fun isMarkdown(file: DocumentFile): Boolean {
            if (!file.isFile) return false
            val name = file.name
            val mime = file.type
            val looksLikeMarkdown = name?.endsWith(".md", ignoreCase = true) == true
            val isTextMarkdown =
                mime.equals("text/markdown", ignoreCase = true) ||
                    mime.equals("text/x-markdown", ignoreCase = true)
            val isPlainText = mime.equals("text/plain", ignoreCase = true)
            return looksLikeMarkdown || isTextMarkdown || isPlainText
        }

        fun shouldSkipDir(path: String): Boolean {
            val p = path.trimStart('/').replace('\\', '/')
            if (p.equals(appConfigDirName, ignoreCase = true)) return true
            if (p.startsWith("$appConfigDirName/", ignoreCase = true)) return true
            return false
        }

        fun walk(dir: DocumentFile, prefix: String) {
            for (child in dir.listFiles()) {
                val name = child.name ?: continue
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                if (child.isDirectory) {
                    if (shouldSkipDir(path)) continue
                    walk(child, path)
                } else if (child.isFile && isMarkdown(child)) {
                    val lastModified = child.lastModified()
                    docs +=
                        UiDoc(
                            name = name,
                            uri = child.uri,
                            lastModified = lastModified,
                            size = child.length(),
                            baseName = computeDocBaseName(name),
                            createdAt = 0L,
                            createdAtText = "",
                            editedAtText = formatEditedAt(lastModified),
                        )
                }
            }
        }

        walk(root, "")

        val createdAtByUri =
            runCatching { indexRepository.getDocCreatedAtMap(docs.map { it.uri.toString() }) }
                .getOrElse { emptyMap() }

        docs
            .map { doc ->
                val createdAt = createdAtByUri[doc.uri.toString()] ?: doc.lastModified
                doc.copy(
                    createdAt = createdAt,
                    createdAtText = if (createdAt > 0L) formatEditedAt(createdAt) else "",
                )
            }
            .sortedWith(
                compareByDescending<UiDoc> { it.lastModified }
                    .thenBy { it.name.lowercase() },
            )
    }

    suspend fun listVaultTreeEntries(
        rootUri: Uri,
        includeNonMarkdownFiles: Boolean = false,
        includeHidden: Boolean = false,
    ): List<VaultTreeEntry> = withContext(Dispatchers.IO) {
        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext emptyList()
        val out = ArrayList<VaultTreeEntry>(256)

        fun isMarkdown(file: DocumentFile): Boolean {
            if (!file.isFile) return false
            val name = file.name
            val mime = file.type
            val looksLikeMarkdown = name?.endsWith(".md", ignoreCase = true) == true
            val isTextMarkdown =
                mime.equals("text/markdown", ignoreCase = true) ||
                    mime.equals("text/x-markdown", ignoreCase = true)
            val isPlainText = mime.equals("text/plain", ignoreCase = true)
            return looksLikeMarkdown || isTextMarkdown || isPlainText
        }

        fun shouldSkipDir(path: String): Boolean {
            val p = path.trimStart('/').replace('\\', '/')
            if (p.equals(appConfigDirName, ignoreCase = true)) return true
            if (p.startsWith("$appConfigDirName/", ignoreCase = true)) return true
            return false
        }

        suspend fun walk(dir: DocumentFile, prefix: String, depth: Int) {
            currentCoroutineContext().ensureActive()
            val children =
                dir
                    .listFiles()
                    .asSequence()
                    .filter { child ->
                        val name = child.name ?: return@filter false
                        if (!includeHidden && name.startsWith(".")) return@filter false
                        true
                    }
                    .sortedWith(
                        compareByDescending<DocumentFile> { it.isDirectory }
                            .thenBy { it.name?.lowercase().orEmpty() },
                    )
                    .toList()

            for (child in children) {
                val name = child.name ?: continue
                if (child.isDirectory) {
                    val dirPath = if (prefix.isBlank()) "$name/" else "$prefix$name/"
                    if (shouldSkipDir(dirPath)) continue
                    out +=
                        VaultTreeEntry(
                            relativePath = dirPath,
                            name = name,
                            uri = child.uri,
                            isDirectory = true,
                            parentPath = prefix.takeIf { it.isNotBlank() },
                            depth = depth,
                        )
                    walk(child, dirPath, depth + 1)
                } else if (child.isFile && (includeNonMarkdownFiles || isMarkdown(child))) {
                    val filePath = if (prefix.isBlank()) name else "$prefix$name"
                    out +=
                        VaultTreeEntry(
                            relativePath = filePath,
                            name = name,
                            uri = child.uri,
                            isDirectory = false,
                            parentPath = prefix.takeIf { it.isNotBlank() },
                            depth = depth,
                        )
                }
            }
            yield()
        }

        walk(root, prefix = "", depth = 0)
        out
    }

    private fun normalizeDirPath(path: String?): String? {
        val cleaned =
            path
                .orEmpty()
                .replace('\\', '/')
                .trim()
                .trimStart('/')
                .let { p -> if (p.isBlank()) "" else p.trimEnd('/') + "/" }
        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun shouldSkipDirIndexPath(path: String): Boolean {
        val p = path.trimStart('/').replace('\\', '/')
        if (p.equals(appConfigDirName, ignoreCase = true)) return true
        if (p.startsWith("$appConfigDirName/", ignoreCase = true)) return true
        return false
    }

    private fun isMarkdownDocument(file: DocumentFile): Boolean {
        if (!file.isFile) return false
        val name = file.name
        val mime = file.type
        val looksLikeMarkdown = name?.endsWith(".md", ignoreCase = true) == true
        val isTextMarkdown =
            mime.equals("text/markdown", ignoreCase = true) ||
                mime.equals("text/x-markdown", ignoreCase = true)
        val isPlainText = mime.equals("text/plain", ignoreCase = true)
        return looksLikeMarkdown || isTextMarkdown || isPlainText
    }

    private suspend fun listVaultChildrenDirIndexEntriesLive(
        rootUri: Uri,
        parentRelativePath: String?,
        includeNonMarkdownFiles: Boolean,
        includeHidden: Boolean,
    ): List<VaultDirIndexRepository.DirIndexEntry> = withContext(Dispatchers.IO) {
        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext emptyList()
        val parentPathKey = normalizeDirPath(parentRelativePath)
        if (parentPathKey != null && shouldSkipDirIndexPath(parentPathKey)) return@withContext emptyList()

        var dir: DocumentFile = root
        if (parentPathKey != null) {
            val parts = parentPathKey.trimEnd('/').split('/').filter { it.isNotBlank() }
            for (part in parts) {
                val next = findChild(dir, part) ?: return@withContext emptyList()
                if (!next.isDirectory) return@withContext emptyList()
                dir = next
            }
        }

        val children =
            dir
                .listFiles()
                .asSequence()
                .filter { child ->
                    val name = child.name ?: return@filter false
                    if (!includeHidden && name.startsWith(".")) return@filter false
                    true
                }
                .sortedWith(
                    compareByDescending<DocumentFile> { it.isDirectory }
                        .thenBy { it.name?.lowercase().orEmpty() },
                )
                .toList()

        val out = ArrayList<VaultDirIndexRepository.DirIndexEntry>(children.size)
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                val dirPath = if (parentPathKey == null) "$name/" else "$parentPathKey$name/"
                if (shouldSkipDirIndexPath(dirPath)) continue
                out +=
                    VaultDirIndexRepository.DirIndexEntry(
                        relativePath = dirPath,
                        parentPath = parentPathKey,
                        name = name,
                        uri = child.uri.toString(),
                        isDirectory = true,
                        lastModified = child.lastModified(),
                    )
            } else if (child.isFile && (includeNonMarkdownFiles || isMarkdownDocument(child))) {
                val filePath = if (parentPathKey == null) name else "$parentPathKey$name"
                out +=
                    VaultDirIndexRepository.DirIndexEntry(
                        relativePath = filePath,
                        parentPath = parentPathKey,
                        name = name,
                        uri = child.uri.toString(),
                        isDirectory = false,
                        lastModified = child.lastModified(),
                    )
            }
        }
        out
    }

    suspend fun listVaultChildrenEntries(
        rootUri: Uri,
        parentRelativePath: String?,
        includeNonMarkdownFiles: Boolean = false,
        includeHidden: Boolean = false,
    ): List<VaultTreeEntry> = withContext(Dispatchers.IO) {
        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext emptyList()

        fun isMarkdown(file: DocumentFile): Boolean {
            if (!file.isFile) return false
            val name = file.name
            val mime = file.type
            val looksLikeMarkdown = name?.endsWith(".md", ignoreCase = true) == true
            val isTextMarkdown =
                mime.equals("text/markdown", ignoreCase = true) ||
                    mime.equals("text/x-markdown", ignoreCase = true)
            val isPlainText = mime.equals("text/plain", ignoreCase = true)
            return looksLikeMarkdown || isTextMarkdown || isPlainText
        }

        fun shouldSkipDir(path: String): Boolean {
            val p = path.trimStart('/').replace('\\', '/')
            if (p.equals(appConfigDirName, ignoreCase = true)) return true
            if (p.startsWith("$appConfigDirName/", ignoreCase = true)) return true
            return false
        }

        val cleanedParent =
            parentRelativePath
                .orEmpty()
                .replace('\\', '/')
                .trim()
                .trimStart('/')
                .let { p -> if (p.isBlank()) "" else p.trimEnd('/') + "/" }
        if (cleanedParent.isNotBlank() && shouldSkipDir(cleanedParent)) return@withContext emptyList()

        var dir: DocumentFile = root
        if (cleanedParent.isNotBlank()) {
            val parts = cleanedParent.trimEnd('/').split('/').filter { it.isNotBlank() }
            for (part in parts) {
                val next = findChild(dir, part) ?: return@withContext emptyList()
                if (!next.isDirectory) return@withContext emptyList()
                dir = next
            }
        }

        val parentPathKey = cleanedParent.takeIf { it.isNotBlank() }
        val depth = parentPathKey?.count { it == '/' } ?: 0
        val children =
            dir
                .listFiles()
                .asSequence()
                .filter { child ->
                    val name = child.name ?: return@filter false
                    if (!includeHidden && name.startsWith(".")) return@filter false
                    true
                }
                .sortedWith(
                    compareByDescending<DocumentFile> { it.isDirectory }
                        .thenBy { it.name?.lowercase().orEmpty() },
                )
                .toList()

        val out = ArrayList<VaultTreeEntry>(children.size)
        for (child in children) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                val dirPath = if (cleanedParent.isBlank()) "$name/" else "$cleanedParent$name/"
                if (shouldSkipDir(dirPath)) continue
                out +=
                    VaultTreeEntry(
                        relativePath = dirPath,
                        name = name,
                        uri = child.uri,
                        isDirectory = true,
                        parentPath = parentPathKey,
                        depth = depth,
                    )
            } else if (child.isFile && (includeNonMarkdownFiles || isMarkdown(child))) {
                val filePath = if (cleanedParent.isBlank()) name else "$cleanedParent$name"
                out +=
                    VaultTreeEntry(
                        relativePath = filePath,
                        name = name,
                        uri = child.uri,
                        isDirectory = false,
                        parentPath = parentPathKey,
                        depth = depth,
                    )
            }
        }
        out
    }

    suspend fun ensureDirIndexBuilt(
        rootUri: Uri,
        force: Boolean = false,
    ) {
        val rootKey = rootUri.toString().trim()
        if (rootKey.isBlank()) return
        if (!force) {
            val has = runCatching { dirIndexRepository.hasAnyEntries(rootKey) }.getOrDefault(false)
            if (has) return
        }

        dirIndexBuildLock.withLock {
            if (dirIndexBuildInProgress) return@withLock
            if (!force) {
                val has = runCatching { dirIndexRepository.hasAnyEntries(rootKey) }.getOrDefault(false)
                if (has) return@withLock
            }
            dirIndexBuildInProgress = true
            try {
                rebuildDirIndex(rootUri)
            } finally {
                dirIndexBuildInProgress = false
            }
        }
    }

    suspend fun rebuildDirIndex(
        rootUri: Uri,
        includeNonMarkdownFiles: Boolean = false,
        includeHidden: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext
        val rootKey = rootUri.toString()
        val out = ArrayList<VaultDirIndexRepository.DirIndexEntry>(512)

        suspend fun walk(dir: DocumentFile, prefix: String?) {
            currentCoroutineContext().ensureActive()
            val children =
                dir
                    .listFiles()
                    .asSequence()
                    .filter { child ->
                        val name = child.name ?: return@filter false
                        if (!includeHidden && name.startsWith(".")) return@filter false
                        true
                    }
                    .sortedWith(
                        compareByDescending<DocumentFile> { it.isDirectory }
                            .thenBy { it.name?.lowercase().orEmpty() },
                    )
                    .toList()

            val parentKey = normalizeDirPath(prefix)
            for (child in children) {
                val name = child.name ?: continue
                if (child.isDirectory) {
                    val dirPath = if (parentKey == null) "$name/" else "$parentKey$name/"
                    if (shouldSkipDirIndexPath(dirPath)) continue
                    out +=
                        VaultDirIndexRepository.DirIndexEntry(
                            relativePath = dirPath,
                            parentPath = parentKey,
                            name = name,
                            uri = child.uri.toString(),
                            isDirectory = true,
                            lastModified = child.lastModified(),
                        )
                    walk(child, dirPath)
                } else if (child.isFile && (includeNonMarkdownFiles || isMarkdownDocument(child))) {
                    val filePath = if (parentKey == null) name else "$parentKey$name"
                    out +=
                        VaultDirIndexRepository.DirIndexEntry(
                            relativePath = filePath,
                            parentPath = parentKey,
                            name = name,
                            uri = child.uri.toString(),
                            isDirectory = false,
                            lastModified = child.lastModified(),
                        )
                }
            }
            yield()
        }

        walk(root, prefix = "")
        val builtAt = System.currentTimeMillis()
        runCatching { dirIndexRepository.replaceAll(rootUri = rootKey, entries = out, builtAtMs = builtAt) }
    }

    suspend fun refreshDirIndexForDirectory(
        rootUri: Uri,
        parentRelativePath: String?,
        includeNonMarkdownFiles: Boolean = false,
        includeHidden: Boolean = false,
    ) {
        val rootKey = rootUri.toString().trim()
        if (rootKey.isBlank()) return
        // Only refresh if an index exists; otherwise let the full build happen in background.
        val has = runCatching { dirIndexRepository.hasAnyEntries(rootKey) }.getOrDefault(false)
        if (!has) return
        val parentKey = normalizeDirPath(parentRelativePath)
        if (parentKey != null && shouldSkipDirIndexPath(parentKey)) return

        val children =
            runCatching {
                listVaultChildrenDirIndexEntriesLive(
                    rootUri = rootUri,
                    parentRelativePath = parentKey,
                    includeNonMarkdownFiles = includeNonMarkdownFiles,
                    includeHidden = includeHidden,
                )
            }.getOrElse { emptyList() }
        val builtAt = System.currentTimeMillis()
        runCatching {
            dirIndexRepository.replaceChildren(
                rootUri = rootKey,
                parentPath = parentKey,
                children = children,
                builtAtMs = builtAt,
            )
        }
    }

    suspend fun listVaultChildrenEntriesIndexed(
        rootUri: Uri,
        parentRelativePath: String?,
    ): List<VaultTreeEntry> {
        val rootKey = rootUri.toString().trim()
        if (rootKey.isBlank()) return emptyList()
        val parentKey = normalizeDirPath(parentRelativePath)
        if (parentKey != null && shouldSkipDirIndexPath(parentKey)) return emptyList()
        return runCatching { dirIndexRepository.listChildren(rootUri = rootKey, parentPath = parentKey) }.getOrElse { emptyList() }
    }

    suspend fun findDocByName(rootUri: Uri, name: String): UiDoc? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@withContext null
        val target =
            if (trimmed.endsWith(".md", ignoreCase = true)) trimmed
            else "$trimmed.md"
        val docs = listMarkdownDocs(rootUri)
        docs.firstOrNull { it.name.equals(target, ignoreCase = true) }
            ?: docs.firstOrNull { computeDocBaseName(it.name).equals(trimmed, ignoreCase = true) }
    }

    suspend fun rebuildIndex(
        rootUri: Uri,
        forceScan: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        indexBuildInProgress = true
        try {
            ensureVaultStructure(rootUri)
            // Reuse the most recent doc listing when available to avoid repeated full directory scans.
            val docs = listMarkdownDocsCached(rootUri, force = forceScan, maxStaleMs = 3 * 60_000L)

            val keepUris = docs.map { it.uri.toString() }.toHashSet()
            val stale =
                runCatching { indexRepository.listAllDocUris() }
                    .getOrElse { emptyList() }
                    .filterNot { keepUris.contains(it) }
            if (stale.isNotEmpty()) {
                runCatching { indexRepository.deleteDocumentsByUriStrings(stale) }
                    .onFailure { Log.e("Zhixu", "Index prune failed", it) }
            }

            val coroutineContext = currentCoroutineContext()
            for (doc in docs) {
                coroutineContext.ensureActive()
                runCatching {
                    val content = readText(doc.uri)
                    indexRepository.indexDocument(doc, content)
                }.onFailure {
                    Log.e("Zhixu", "Index failed for ${doc.uri}", it)
                }
                yield()
            }
            signalIndexChanged()
        } finally {
            indexBuildInProgress = false
        }
    }

    suspend fun search(rootUri: Uri?, query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        val indexed =
            runCatching { indexRepository.search(q) }
                .getOrElse {
                    Log.e("Zhixu", "Search failed", it)
                    emptyList()
                }
        if (indexed.isNotEmpty()) return@withContext indexed

        val root = rootUri ?: return@withContext indexed
        fallbackSearchDocs(root, q, limit = 50)
    }

    suspend fun getTodayTasks(
        status: VaultIndexRepository.TaskStatusFilter = VaultIndexRepository.TaskStatusFilter.Undone,
        tag: String? = null,
    ): List<UiTask> =
        runCatching { indexRepository.getTodayTasks(status = status, tag = tag) }
            .getOrElse {
                Log.e("Zhixu", "getTodayTasks failed", it)
                emptyList()
            }

    suspend fun getUpcomingTasks(
        status: VaultIndexRepository.TaskStatusFilter = VaultIndexRepository.TaskStatusFilter.Undone,
        tag: String? = null,
    ): List<UiTask> =
        runCatching { indexRepository.getUpcomingTasks(status = status, tag = tag) }
            .getOrElse {
                Log.e("Zhixu", "getUpcomingTasks failed", it)
                emptyList()
            }

    suspend fun getAllTasks(
        limit: Int = 200,
        status: VaultIndexRepository.TaskStatusFilter = VaultIndexRepository.TaskStatusFilter.Undone,
        tag: String? = null,
    ): List<UiTask> =
        runCatching { indexRepository.getAllTasks(limit = limit, status = status, tag = tag) }
            .getOrElse {
                Log.e("Zhixu", "getAllTasks failed", it)
                emptyList()
            }

    suspend fun getRecentCompletedTasks(limit: Int = 50): List<UiTask> =
        runCatching { indexRepository.getRecentCompletedTasks(limit) }
            .getOrElse {
                Log.e("Zhixu", "getRecentCompletedTasks failed", it)
                emptyList()
            }

    suspend fun addTaskToInbox(rootUri: Uri, title: String): Boolean = withContext(Dispatchers.IO) {
        addTaskToInbox(
            rootUri = rootUri,
            title = title,
            dueDate = null,
            dueTime = null,
            tags = emptyList(),
            priority = null,
        )
    }

    suspend fun addTaskToInbox(
        rootUri: Uri,
        title: String,
        dueDate: LocalDate?,
        dueTime: LocalTime?,
        tags: List<String>,
        priority: Int?,
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return@withContext false
        ensureVaultStructure(rootUri)
        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext false
        val inbox =
            findChild(root, "Inbox.md")
                ?: runCatching {
                    val docsDir = findChild(root, "docs")?.takeIf { it.isDirectory }
                    docsDir?.let { findChild(it, "Inbox.md") }
                }.getOrNull()
                ?: createMarkdownFile(root, "Inbox.md")
                ?: return@withContext false

        val createdAtMs = System.currentTimeMillis()
        val taskId = Ulid.next()

        val fields = buildString {
            val date = dueDate
            if (date != null) {
                val time = dueTime ?: LocalTime.of(9, 0)
                append(" @due(")
                append(dueFormat.format(date.atTime(time)))
                append(")")
            }
            val p = priority
            if (p != null) {
                append(" @priority(")
                append(p.coerceIn(1, 4))
                append(")")
            }
            for (tag in tags.map { it.trim() }.filter { it.isNotBlank() }.distinct()) {
                append(" @tag(")
                append(tag)
                append(")")
            }
        }

        val before = readText(inbox.uri)
        val prefix = if (before.isNotEmpty() && !before.endsWith("\n")) "\n" else ""
        val withLine = before + prefix + "- [ ] $trimmed$fields @id($taskId)\n"
        val normalized = TaskSyntax.normalizeMarkdown(withLine).markdown
        writeText(inbox.uri, normalized)
        indexDocUri(inbox.uri)
        recordDailyDocEdited(docUri = inbox.uri, day = LocalDate.now())
        runCatching { indexRepository.recordTaskCreated(taskId = taskId, docUri = inbox.uri.toString(), createdEpochMs = createdAtMs) }
        true
    }

    suspend fun createDoc(rootUri: Uri, fileName: String): UiDoc = withContext(Dispatchers.IO) {
        val root = vaultRootToDocumentFile(context, rootUri) ?: error("Invalid vault root Uri")
        val baseName = sanitizeFileName(fileName).removeSuffix(".md").trim().ifBlank { "Untitled" }
        val (created, createdName) = createUniqueMarkdownFile(root, baseName) ?: error("Failed to create document")
        val createdAtMs = System.currentTimeMillis()
        runCatching { indexRepository.upsertDocCreatedAt(created.uri.toString(), createdAtMs) }
        signalIndexChanged()
        if (created.name?.endsWith(".md", ignoreCase = true) != true) {
            // Some providers may ignore extension; best-effort rename to keep the vault consistent.
            runCatching { created.renameTo(createdName) }
        }
        val name = created.name ?: createdName
        val lastModified = created.lastModified()
        runCatching {
            indexRepository.indexDocument(
                UiDoc(
                    name = name,
                    uri = created.uri,
                    lastModified = lastModified,
                    size = created.length(),
                    baseName = computeDocBaseName(name),
                    createdAt = createdAtMs,
                    createdAtText = "",
                    editedAtText = "",
                ),
                "",
            )
        }
        signalIndexChanged()
        recordDailyDocEdited(docUri = created.uri, day = LocalDate.now())
        runCatching { upsertDirIndexEntryForFileIfPresent(rootUri, created) }
        UiDoc(
            name = name,
            uri = created.uri,
            lastModified = lastModified,
            size = created.length(),
            baseName = computeDocBaseName(name),
            createdAt = createdAtMs,
            createdAtText = formatEditedAt(createdAtMs),
            editedAtText = formatEditedAt(lastModified),
        )
    }

    suspend fun deleteDoc(docUri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (docUri.scheme.equals("file", ignoreCase = true)) {
            val path = docUri.path ?: return@withContext false
            val ok = runCatching { File(path).delete() }.getOrDefault(false)
            if (ok) {
                indexRepository.deleteDocument(docUri)
                signalIndexChanged()
            }
            return@withContext ok
        }

        // DocumentFile.fromSingleUri().delete() can be unreliable across providers; DocumentsContract is more direct.
        val ok = runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }.getOrDefault(false)
        if (ok) {
            indexRepository.deleteDocument(docUri)
            signalIndexChanged()
        }
        ok
    }

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return@withContext ""
            return@withContext runCatching { File(path).readText(Charsets.UTF_8) }.getOrDefault("")
        }
        val resolver: ContentResolver = context.contentResolver
        resolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        } ?: ""
    }

    suspend fun readBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return@withContext null
            return@withContext runCatching { File(path).readBytes() }.getOrNull()
        }
        val resolver: ContentResolver = context.contentResolver
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: error("Invalid file uri: $uri")
            File(path).parentFile?.mkdirs()
            File(path).writeText(text, Charsets.UTF_8)
            return@withContext
        }
        val resolver: ContentResolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
        } ?: error("Failed to open output stream for $uri")
    }

    suspend fun renameDoc(docUri: Uri, desiredName: String): Uri? = withContext(Dispatchers.IO) {
        val sanitizedBase = sanitizeFileName(desiredName).removeSuffix(".md").trim().ifBlank { "Untitled" }
        val normalizedBase = sanitizedBase.removeSuffix(".md").trim().ifBlank { "Untitled" }
        val finalName = if (normalizedBase.endsWith(".md", ignoreCase = true)) normalizedBase else "$normalizedBase.md"

        // file:// vault: handle with java.io.File rename (DocumentsContract doesn't apply).
        if (docUri.scheme.equals("file", ignoreCase = true)) {
            val path = docUri.path ?: return@withContext null
            val file = File(path)
            val dir = file.parentFile ?: return@withContext null
            if (!dir.exists()) return@withContext null

            val currentName = file.name
            var candidate = finalName
            var index = 1
            while (File(dir, candidate).exists() && !candidate.equals(currentName, ignoreCase = true)) {
                candidate = "$normalizedBase ($index).md"
                index++
            }
            if (candidate.equals(currentName, ignoreCase = true)) return@withContext docUri

            val dest = File(dir, candidate)
            val ok = runCatching { file.renameTo(dest) }.getOrDefault(false)
            if (!ok) return@withContext null

            val newUri = Uri.fromFile(dest)
            runCatching { indexRepository.migrateDocUri(oldUri = docUri.toString(), newUri = newUri.toString(), newName = dest.name) }
                .onSuccess { signalIndexChanged() }
            return@withContext newUri
        }

        val resolver: ContentResolver = context.contentResolver
        val renamedUri =
            runCatching { DocumentsContract.renameDocument(resolver, docUri, finalName) }
                .getOrNull()
        if (renamedUri != null) {
            val newName = DocumentFile.fromSingleUri(context, renamedUri)?.name ?: finalName
            runCatching { indexRepository.migrateDocUri(oldUri = docUri.toString(), newUri = renamedUri.toString(), newName = newName) }
                .onSuccess { signalIndexChanged() }
            return@withContext renamedUri
        }

        val doc = DocumentFile.fromSingleUri(context, docUri) ?: return@withContext null
        val ok = runCatching { doc.renameTo(finalName) }.getOrDefault(false)
        if (!ok) return@withContext null
        // Some providers rename in-place without changing the Uri; refresh the index so doc name updates.
        runCatching { indexDocUri(docUri) }
        docUri
    }

    suspend fun appendText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: error("Invalid file uri: $uri")
            File(path).parentFile?.mkdirs()
            File(path).appendText(text, Charsets.UTF_8)
            return@withContext
        }
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val resolver: ContentResolver = context.contentResolver
        val appended =
            runCatching {
                resolver.openOutputStream(uri, "wa")?.use { output ->
                    output.write(bytes)
                } ?: false
            }.getOrElse { false }
        if (appended == false) {
            // Fallback: read + rewrite (best-effort for providers that don't support append mode).
            val before = readText(uri)
            writeText(uri, before + text)
        }
    }

    suspend fun indexDocUri(docUri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            val text = readText(docUri)
            val stat = DocumentFile.fromSingleUri(context, docUri)
            val indexedName = runCatching { indexRepository.getIndexedDocName(docUri.toString()) }.getOrNull()
            val resolvedName =
                stat?.name
                    ?.takeIf { it.isNotBlank() }
                    ?: indexedName
                    ?: runCatching { statDocUi(docUri)?.name }.getOrNull()
            val uiDoc =
                UiDoc(
                    name = resolvedName ?: "Document.md",
                    uri = docUri,
                    lastModified = stat?.lastModified() ?: 0L,
                    size = stat?.length() ?: text.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                )
            indexRepository.indexDocument(uiDoc, text)
            signalIndexChanged()
        }.onFailure {
            Log.e("Zhixu", "indexDocUri failed for $docUri", it)
        }
    }

    suspend fun taskStats(
        rootUri: Uri,
        days: Int = 98,
        maxDocsToScan: Int = 200,
    ): TaskStats = withContext(Dispatchers.IO) {
        val docs = listMarkdownDocs(rootUri).take(maxDocsToScan)
        val cutoff = LocalDate.now().minusDays((days - 1).coerceAtLeast(0).toLong())
        val donePerDay = HashMap<LocalDate, Int>()
        var done = 0
        var total = 0

        for ((idx, doc) in docs.withIndex()) {
            currentCoroutineContext().ensureActive()
            val content = readText(doc.uri)
            val tasks = TaskSyntax.parseTasks(content)
            total += tasks.size

            for (task in tasks) {
                if (task.checked) done++
                val doneAt = task.done ?: continue
                val date =
                    runCatching { LocalDateTime.parse(doneAt, dueFormat).toLocalDate() }
                        .getOrNull()
                        ?: continue
                if (date.isBefore(cutoff)) continue
                donePerDay[date] = (donePerDay[date] ?: 0) + 1
            }

            if (idx % 8 == 0) yield()
        }

        TaskStats(done = done, total = total, donePerDay = donePerDay)
    }

    suspend fun recordDailyDocEdited(
        docUri: Uri,
        day: LocalDate = LocalDate.now(),
    ) {
        runCatching {
            indexRepository.recordDailyDocEdited(docUri = docUri.toString(), day = day)
        }.onFailure {
            Log.e("Zhixu", "recordDailyDocEdited failed", it)
        }
    }

    suspend fun recordDailyTasksDone(
        delta: Int,
        day: LocalDate = LocalDate.now(),
    ) {
        if (delta <= 0) return
        runCatching {
            indexRepository.incrementDailyTasksDone(day = day, delta = delta)
        }.onFailure {
            Log.e("Zhixu", "recordDailyTasksDone failed", it)
        }
    }

    suspend fun getDailyContribForYear(
        year: Int,
    ): Map<LocalDate, DailyContrib> = withContext(Dispatchers.IO) {
        val yearStart = LocalDate.of(year, 1, 1)
        val yearEnd = LocalDate.of(year, 12, 31)
        runCatching { indexRepository.getDailyContribSince(yearStart) }
            .getOrElse {
                Log.e("Zhixu", "getDailyContribForYear failed", it)
                emptyMap()
            }
            .filterKeys { d -> !d.isAfter(yearEnd) }
    }

    suspend fun getDocsCreatedOn(day: LocalDate): List<UiDoc> =
        runCatching { indexRepository.getDocsCreatedOn(day = day) }.getOrElse { emptyList() }

    suspend fun getTasksCreatedOn(day: LocalDate): List<UiTask> =
        runCatching { indexRepository.getTasksCreatedOn(day = day) }.getOrElse { emptyList() }

    suspend fun writeBytes(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: error("Invalid file uri: $uri")
            File(path).parentFile?.mkdirs()
            File(path).writeBytes(bytes)
            return@withContext
        }
        val resolver: ContentResolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(bytes)
        } ?: error("Failed to open output stream for $uri")
    }

    private suspend fun fallbackSearchDocs(rootUri: Uri, query: String, limit: Int): List<SearchResult> {
        val docs = listMarkdownDocs(rootUri)
        val out = ArrayList<SearchResult>(limit)
        val seen = HashSet<String>()

        val maxDocsToScan = 200
        for (doc in docs.take(maxDocsToScan)) {
            if (out.size >= limit) break
            val uriStr = doc.uri.toString()
            if (!seen.add(uriStr)) continue

            val titleHit = doc.name.contains(query, ignoreCase = true)
            var snippet: String? = null
            var contentHit = false
            if (!titleHit) {
                val content = readText(doc.uri)
                contentHit = content.contains(query, ignoreCase = true)
                if (contentHit) snippet = buildSnippet(content, query)
            }

            if (titleHit || contentHit) {
                out += DocSearchResult(title = doc.name, uri = doc.uri, snippet = snippet)
            }
        }

        return out
    }

    private fun buildSnippet(content: String, query: String, maxLen: Int = 120): String? {
        val text = content.replace('\n', ' ').trim()
        if (text.isBlank()) return null
        val token = query.split(Regex("""\s+""")).firstOrNull { it.isNotBlank() } ?: return text.take(maxLen)
        val idx = text.indexOf(token, ignoreCase = true)
        if (idx < 0) return text.take(maxLen)
        val start = (idx - maxLen / 3).coerceAtLeast(0)
        val end = (start + maxLen).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).trim() + suffix
    }

    suspend fun exportIndexSqlite(rootUri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            val root = vaultRootToDocumentFile(context, rootUri) ?: return@runCatching
            val zhixu = findChild(root, ".zhixu") ?: root.createDirectory(".zhixu") ?: return@runCatching
            val indexFile = findChild(zhixu, "index.sqlite") ?: createFileExact(zhixu, "application/x-sqlite3", "index.sqlite")
            val dbFile: File = context.getDatabasePath("vault_index.db")
            if (indexFile != null && dbFile.exists()) {
                writeBytes(indexFile.uri, dbFile.readBytes())
            }
        }.onFailure {
            Log.e("Zhixu", "exportIndexSqlite failed", it)
        }
    }

    suspend fun toggleTask(docUri: Uri, lineIndex: Int): String = withContext(Dispatchers.IO) {
        val before = readText(docUri)
        val after = TaskSyntax.toggleTaskAtLine(before, lineIndex)
        if (after != before) {
            val beforeChecked =
                runCatching { TaskSyntax.parseTasks(before).firstOrNull { it.lineIndex == lineIndex }?.checked }
                    .getOrNull()
                    ?: false
            val afterChecked =
                runCatching { TaskSyntax.parseTasks(after).firstOrNull { it.lineIndex == lineIndex }?.checked }
                    .getOrNull()
                    ?: false
            writeText(docUri, after)
            val docName = DocumentFile.fromSingleUri(context, docUri)?.name ?: "Document"
            val stat = DocumentFile.fromSingleUri(context, docUri)
            val lastModified = stat?.lastModified() ?: 0L
            val uiDoc =
                UiDoc(
                    name = docName,
                    uri = docUri,
                    lastModified = lastModified,
                    size = stat?.length() ?: after.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                    baseName = computeDocBaseName(docName),
                    editedAtText = if (lastModified == 0L) "" else formatEditedAt(lastModified),
                )
            runCatching { indexRepository.indexDocument(uiDoc, after) }
                .onSuccess { signalIndexChanged() }
                .onFailure { Log.e("Zhixu", "Index update failed for $docUri", it) }

            recordDailyDocEdited(docUri = docUri)
            if (!beforeChecked && afterChecked) {
                recordDailyTasksDone(delta = 1)
            }
        }
        after
    }

    private fun sanitizeFileName(name: String): String =
        name.trim()
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .ifBlank { "Untitled" }

    private fun createUniqueMarkdownFile(docsDir: DocumentFile, baseName: String): Pair<DocumentFile, String>? {
        val normalizedBase = baseName.ifBlank { "Untitled" }
        val maxAttempts = 200
        for (index in 0 until maxAttempts) {
            val candidate =
                if (index == 0) "$normalizedBase.md"
                else "$normalizedBase ($index).md"

            val created =
                if (docsDir.uri.scheme.equals("file", ignoreCase = true)) {
                    val dir = docsDir.uri.path?.let(::File) ?: return null
                    if (!dir.exists()) dir.mkdirs()
                    val file = File(dir, candidate)
                    if (file.exists()) null
                    else {
                        runCatching {
                            file.createNewFile()
                            DocumentFile.fromFile(file)
                        }.getOrNull()
                    }
                } else {
                    docsDir.createFile("text/markdown", candidate) ?: docsDir.createFile("text/plain", candidate)
                }

            if (created != null) return Pair(created, candidate)
        }
        return null
    }

    private suspend fun upsertDirIndexEntryForFileIfPresent(rootUri: Uri, file: DocumentFile) {
        val rootKey = rootUri.toString().trim()
        if (rootKey.isBlank()) return
        val has = runCatching { dirIndexRepository.hasAnyEntries(rootKey) }.getOrDefault(false)
        if (!has) return

        val rel = computeRelativePath(rootUri, file.uri)?.replace('\\', '/')?.trimStart('/') ?: return
        val name = file.name ?: rel.substringAfterLast('/').ifBlank { return }
        val parentRaw =
            rel.lastIndexOf('/').takeIf { it >= 0 }?.let { idx ->
                rel.substring(0, idx + 1)
            }
        val parentKey = normalizeDirPath(parentRaw)
        val builtAt = System.currentTimeMillis()
        dirIndexRepository.upsertEntry(
            rootUri = rootKey,
            entry =
                VaultDirIndexRepository.DirIndexEntry(
                    relativePath = rel,
                    parentPath = parentKey,
                    name = name,
                    uri = file.uri.toString(),
                    isDirectory = false,
                    lastModified = file.lastModified(),
                ),
            builtAtMs = builtAt,
        )
    }

    private fun findChild(parent: DocumentFile, name: String): DocumentFile? {
        // DocumentFile.findFile() can be unreliable on some providers; listing is more robust.
        parent.listFiles().firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        return parent.findFile(name)
    }

    private fun createMarkdownFile(parent: DocumentFile, displayName: String): DocumentFile? {
        val mdName = displayName.trim().let { if (it.endsWith(".md", ignoreCase = true)) it else "$it.md" }
        if (parent.uri.scheme.equals("file", ignoreCase = true)) {
            val dir = parent.uri.path?.let(::File) ?: return null
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, mdName)
            runCatching { if (!file.exists()) file.createNewFile() }
            return DocumentFile.fromFile(file)
        }
        return parent.createFile("text/markdown", mdName) ?: parent.createFile("text/plain", mdName)
    }

    private fun createFileExact(parent: DocumentFile, mimeType: String, displayName: String): DocumentFile? {
        if (parent.uri.scheme.equals("file", ignoreCase = true)) {
            val dir = parent.uri.path?.let(::File) ?: return null
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, displayName)
            runCatching { if (!file.exists()) file.createNewFile() }
            return DocumentFile.fromFile(file)
        }
        return parent.createFile(mimeType, displayName)
    }
}

private val editedAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

private fun formatEditedAt(lastModifiedMs: Long): String {
    val instant = Instant.ofEpochMilli(lastModifiedMs)
    val local = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    return editedAtFormatter.format(local)
}

private fun computeDocBaseName(name: String): String =
    if (name.endsWith(".md", ignoreCase = true)) name.dropLast(3) else name
