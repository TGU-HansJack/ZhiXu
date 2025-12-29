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

data class TaskStats(
    val done: Int,
    val total: Int,
    val donePerDay: Map<LocalDate, Int>,
)

class VaultRepository(
    private val context: Context,
) {
    private val indexRepository = VaultIndexRepository(context)
    private val dueFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

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

    suspend fun getDocsDirUri(rootUri: Uri): Uri? =
        docListCacheLock.withLock {
            val key = rootUri.toString()
            val cached = synchronized(docListCache) { docListCache[key]?.docsDirUri }
            if (cached != null) return@withLock cached

            val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withLock null
            val docsDir = findChild(root, "docs") ?: return@withLock null
            val uri = docsDir.uri
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
            val docsDirUri =
                runCatching {
                    val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@runCatching null
                    findChild(root, "docs")?.uri
                }.getOrNull()

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

    suspend fun ensureVaultStructure(rootUri: Uri) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: error("Invalid vault root Uri")
        val docs = findChild(root, "docs") ?: root.createDirectory("docs")
        val attachments = findChild(root, "attachments") ?: root.createDirectory("attachments")
        val zhixu = findChild(root, ".zhixu") ?: root.createDirectory(".zhixu")
        val syncDir = zhixu?.let { findChild(it, "sync") ?: it.createDirectory("sync") }
        val exportsDir = zhixu?.let { findChild(it, "exports") ?: it.createDirectory("exports") }
        val pluginsDir = zhixu?.let { findChild(it, "plugins") ?: it.createDirectory("plugins") }
        val pluginState =
            pluginsDir?.let {
                findChild(it, "state.json") ?: it.createFile("application/json", "state.json")
            }
        val settings = zhixu?.let { findChild(it, "settings.json") ?: it.createFile("application/json", "settings.json") }
        if (settings != null && settings.length() == 0L) {
            writeText(settings.uri, "{}\n")
        }
        if (pluginState != null && pluginState.length() == 0L) {
            writeText(pluginState.uri, "{\n  \"enabled\": []\n}\n")
        }

        if (docs != null && findChild(docs, "Inbox.md") == null) {
            val inbox = createMarkdownFile(docs, "Inbox.md")
            if (inbox != null) writeText(inbox.uri, "# Inbox\n\n- [ ] First task\n")
        }

        // Touch to keep variables used (and validate creation).
        requireNotNull(docs) { "docs/ directory missing" }
        requireNotNull(attachments) { "attachments/ directory missing" }
        requireNotNull(zhixu) { ".zhixu/ directory missing" }
        // Validate creation of subdirectories too.
        requireNotNull(syncDir) { ".zhixu/sync directory missing" }
        requireNotNull(exportsDir) { ".zhixu/exports directory missing" }
        requireNotNull(pluginsDir) { ".zhixu/plugins directory missing" }
    }

    suspend fun computeVaultTotalSizeBytes(rootUri: Uri): Long = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext 0L

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

    suspend fun listMarkdownDocs(rootUri: Uri): List<UiDoc> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        val docsDir = findChild(root, "docs") ?: return@withContext emptyList()
        val docs =
            docsDir.listFiles()
            .filter { file ->
                if (!file.isFile) return@filter false
                val name = file.name
                val mime = file.type
                val looksLikeMarkdown = name?.endsWith(".md", ignoreCase = true) == true
                val isTextMarkdown =
                    mime.equals("text/markdown", ignoreCase = true) ||
                        mime.equals("text/x-markdown", ignoreCase = true)
                val isPlainText = mime.equals("text/plain", ignoreCase = true)
                looksLikeMarkdown || isTextMarkdown || isPlainText
            }
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val lastModified = file.lastModified()
                UiDoc(
                    name = name,
                    uri = file.uri,
                    lastModified = lastModified,
                    size = file.length(),
                    baseName = computeDocBaseName(name),
                    createdAt = 0L,
                    createdAtText = "",
                    editedAtText = formatEditedAt(lastModified),
                )
            }

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

    suspend fun rebuildIndex(rootUri: Uri) = withContext(Dispatchers.IO) {
        ensureVaultStructure(rootUri)
        val docs = listMarkdownDocs(rootUri)
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
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext false
        val docs = findChild(root, "docs") ?: return@withContext false
        val inbox = findChild(docs, "Inbox.md") ?: createMarkdownFile(docs, "Inbox.md") ?: return@withContext false

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
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: error("Invalid vault root Uri")
        val docsDir = findChild(root, "docs") ?: root.createDirectory("docs") ?: error("Missing docs directory")
        val baseName = sanitizeFileName(fileName).removeSuffix(".md").trim().ifBlank { "Untitled" }
        val createdName = createUniqueMarkdownName(docsDir, baseName)
        val created = createMarkdownFile(docsDir, createdName) ?: error("Failed to create document")
        val createdAtMs = System.currentTimeMillis()
        runCatching { indexRepository.upsertDocCreatedAt(created.uri.toString(), createdAtMs) }
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
        recordDailyDocEdited(docUri = created.uri, day = LocalDate.now())
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
        // DocumentFile.fromSingleUri().delete() can be unreliable across providers; DocumentsContract is more direct.
        val ok = runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }.getOrDefault(false)
        if (ok) {
            indexRepository.deleteDocument(docUri)
        }
        ok
    }

    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        resolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        } ?: ""
    }

    suspend fun readBytes(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        resolver.openInputStream(uri)?.use { it.readBytes() }
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val resolver: ContentResolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(text.toByteArray(StandardCharsets.UTF_8))
        } ?: error("Failed to open output stream for $uri")
    }

    suspend fun renameDoc(docUri: Uri, desiredName: String): Uri? = withContext(Dispatchers.IO) {
        val sanitizedBase = sanitizeFileName(desiredName).removeSuffix(".md").trim().ifBlank { "Untitled" }
        val finalName = if (sanitizedBase.endsWith(".md", ignoreCase = true)) sanitizedBase else "$sanitizedBase.md"
        val resolver: ContentResolver = context.contentResolver
        val renamedUri =
            runCatching { DocumentsContract.renameDocument(resolver, docUri, finalName) }
                .getOrNull()
        if (renamedUri != null) return@withContext renamedUri

        val doc = DocumentFile.fromSingleUri(context, docUri) ?: return@withContext null
        val ok = runCatching { doc.renameTo(finalName) }.getOrDefault(false)
        if (!ok) return@withContext null
        docUri
    }

    suspend fun appendText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
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
            val uiDoc =
                UiDoc(
                    name = stat?.name ?: "Document",
                    uri = docUri,
                    lastModified = stat?.lastModified() ?: 0L,
                    size = stat?.length() ?: text.toByteArray(StandardCharsets.UTF_8).size.toLong(),
                )
            indexRepository.indexDocument(uiDoc, text)
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
            val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@runCatching
            val zhixu = findChild(root, ".zhixu") ?: root.createDirectory(".zhixu") ?: return@runCatching
            val indexFile = findChild(zhixu, "index.sqlite") ?: zhixu.createFile("application/x-sqlite3", "index.sqlite")
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

    private fun createUniqueMarkdownName(docsDir: DocumentFile, baseName: String): String {
        val normalizedBase = baseName.ifBlank { "Untitled" }
        var candidate = "$normalizedBase.md"
        var index = 1
        while (findChild(docsDir, candidate) != null) {
            candidate = "$normalizedBase ($index).md"
            index++
        }
        return candidate
    }

    private fun findChild(parent: DocumentFile, name: String): DocumentFile? {
        // DocumentFile.findFile() can be unreliable on some providers; listing is more robust.
        parent.listFiles().firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        return parent.findFile(name)
    }

    private fun createMarkdownFile(parent: DocumentFile, displayName: String): DocumentFile? {
        val mdName = displayName.trim().let { if (it.endsWith(".md", ignoreCase = true)) it else "$it.md" }
        return parent.createFile("text/markdown", mdName) ?: parent.createFile("text/plain", mdName)
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
