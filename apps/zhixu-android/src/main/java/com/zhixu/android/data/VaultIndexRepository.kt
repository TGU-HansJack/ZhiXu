package com.zhixu.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.DateUtils
import androidx.documentfile.provider.DocumentFile
import com.zhixu.core.tasks.TaskSyntax
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VaultIndexRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val db = VaultIndexDb(context)
    private val dueFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val doneFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    enum class TaskStatusFilter { All, Undone, Done }

    suspend fun hasAnyIndexedDocs(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            database.rawQuery("SELECT 1 FROM docs_fts LIMIT 1", null).use { cursor ->
                cursor.moveToFirst()
            }
        }
    }

    suspend fun indexDocument(
        doc: UiDoc,
        content: String,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                ensureDocsMetaTable(database)
                database.execSQL(
                    "INSERT OR IGNORE INTO docs_meta(uri, created_epoch_ms) VALUES(?, ?)",
                    arrayOf<Any?>(doc.uri.toString(), (doc.lastModified.takeIf { it > 0 } ?: System.currentTimeMillis())),
                )

                database.execSQL("DELETE FROM docs_fts WHERE uri = ?", arrayOf(doc.uri.toString()))
                database.execSQL(
                    "INSERT INTO docs_fts(uri, name, content, last_modified, size) VALUES(?,?,?,?,?)",
                    arrayOf<Any?>(doc.uri.toString(), doc.name, content, doc.lastModified.toString(), doc.size.toString()),
                )

                database.execSQL("DELETE FROM tasks WHERE doc_uri = ?", arrayOf(doc.uri.toString()))
                database.execSQL("DELETE FROM tasks_fts WHERE doc_uri = ?", arrayOf(doc.uri.toString()))

                val tasks = TaskSyntax.parseTasks(content)
                for (task in tasks) {
                    val dueMs = task.due?.let(::parseEpochMillis)
                    val doneMs = task.done?.let(::parseDoneEpochMillis)
                    val tags = task.tags.joinToString(" ") { it.trim().lowercase() }.trim()
                    database.execSQL(
                        """
                        INSERT OR REPLACE INTO tasks(
                          doc_uri, doc_name, line_index, checked, task_id, title, tags, due_epoch_ms, done_epoch_ms, raw_line
                        ) VALUES(?,?,?,?,?,?,?,?,?,?)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            doc.uri.toString(),
                            doc.name,
                            task.lineIndex,
                            if (task.checked) 1 else 0,
                            task.id,
                            task.title,
                            tags,
                            dueMs,
                            doneMs,
                            task.rawLine,
                        ),
                    )
                    database.execSQL(
                        "INSERT INTO tasks_fts(doc_uri, line_index, task_id, title, tags, raw_line) VALUES(?,?,?,?,?,?)",
                        arrayOf<Any?>(doc.uri.toString(), task.lineIndex, task.id, task.title, tags, task.rawLine),
                    )
                }

                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun deleteDocument(docUri: Uri) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                database.execSQL("DELETE FROM docs_fts WHERE uri = ?", arrayOf(docUri.toString()))
                ensureDocsMetaTable(database)
                database.execSQL("DELETE FROM docs_meta WHERE uri = ?", arrayOf(docUri.toString()))
                database.execSQL("DELETE FROM tasks WHERE doc_uri = ?", arrayOf(docUri.toString()))
                database.execSQL("DELETE FROM tasks_fts WHERE doc_uri = ?", arrayOf(docUri.toString()))
                ensureTasksMetaTable(database)
                database.execSQL("DELETE FROM tasks_meta WHERE doc_uri = ?", arrayOf(docUri.toString()))
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun upsertDocCreatedAt(
        docUri: String,
        createdEpochMs: Long,
    ) = withContext(Dispatchers.IO) {
        if (docUri.isBlank() || createdEpochMs <= 0L) return@withContext
        mutex.withLock {
            val database = db.writableDatabase
            ensureDocsMetaTable(database)
            val prev =
                database.rawQuery(
                    "SELECT created_epoch_ms FROM docs_meta WHERE uri = ?",
                    arrayOf(docUri),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else cursor.getLong(0)
                }
            if (prev == null || createdEpochMs < prev) {
                database.execSQL(
                    "INSERT OR REPLACE INTO docs_meta(uri, created_epoch_ms) VALUES(?, ?)",
                    arrayOf<Any?>(docUri, createdEpochMs),
                )
            }
        }
    }

    suspend fun getDocCreatedAtMap(docUris: List<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        if (docUris.isEmpty()) return@withContext emptyMap()
        mutex.withLock {
            val database = db.readableDatabase
            ensureDocsMetaTable(database)

            val out = HashMap<String, Long>(docUris.size)
            val chunkSize = 800
            for (chunk in docUris.chunked(chunkSize)) {
                val placeholders = chunk.joinToString(",") { "?" }
                database.rawQuery(
                    "SELECT uri, created_epoch_ms FROM docs_meta WHERE uri IN ($placeholders)",
                    chunk.toTypedArray(),
                ).use { cursor ->
                    val uriIdx = cursor.getColumnIndexOrThrow("uri")
                    val createdIdx = cursor.getColumnIndexOrThrow("created_epoch_ms")
                    while (cursor.moveToNext()) {
                        out[cursor.getString(uriIdx)] = cursor.getLong(createdIdx)
                    }
                }
            }
            out
        }
    }

    suspend fun getDocsCreatedOn(
        day: LocalDate,
        limit: Int = 200,
    ): List<UiDoc> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        mutex.withLock {
            val database = db.readableDatabase
            ensureDocsMetaTable(database)
            val out = ArrayList<UiDoc>()
            database.rawQuery(
                """
                SELECT m.uri, m.created_epoch_ms, COALESCE(f.name, '') AS name, COALESCE(f.last_modified, '0') AS last_modified, COALESCE(f.size, '0') AS size
                FROM docs_meta m
                LEFT JOIN docs_fts f ON f.uri = m.uri
                WHERE m.created_epoch_ms BETWEEN ? AND ?
                ORDER BY m.created_epoch_ms ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(startMs.toString(), endMs.toString(), limit.toString()),
            ).use { cursor ->
                val uriIdx = cursor.getColumnIndexOrThrow("uri")
                val createdIdx = cursor.getColumnIndexOrThrow("created_epoch_ms")
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val lastModifiedIdx = cursor.getColumnIndexOrThrow("last_modified")
                val sizeIdx = cursor.getColumnIndexOrThrow("size")
                while (cursor.moveToNext()) {
                    val uri = cursor.getString(uriIdx)
                    val indexedName = cursor.getString(nameIdx).orEmpty()
                    val resolvedName =
                        if (indexedName.isNotBlank()) indexedName
                        else resolveDocName(uri).orEmpty().ifBlank { "Document" }
                    val lastModified = cursor.getString(lastModifiedIdx).toLongOrNull() ?: 0L
                    val size = cursor.getString(sizeIdx).toLongOrNull() ?: 0L
                    val created = cursor.getLong(createdIdx)
                    out +=
                        UiDoc(
                            name = resolvedName,
                            uri = Uri.parse(uri),
                            lastModified = lastModified,
                            size = size,
                            baseName = computeDocBaseNameLocal(resolvedName),
                            createdAt = created,
                            createdAtText = "",
                            editedAtText = "",
                        )
                }
            }
            out
        }
    }

    suspend fun recordTaskCreated(
        taskId: String,
        docUri: String,
        createdEpochMs: Long,
    ) = withContext(Dispatchers.IO) {
        if (taskId.isBlank() || docUri.isBlank() || createdEpochMs <= 0L) return@withContext
        mutex.withLock {
            val database = db.writableDatabase
            ensureTasksMetaTable(database)
            database.execSQL(
                "INSERT OR IGNORE INTO tasks_meta(task_id, doc_uri, created_epoch_ms) VALUES(?, ?, ?)",
                arrayOf<Any?>(taskId, docUri, createdEpochMs),
            )
        }
    }

    suspend fun getTasksCreatedOn(
        day: LocalDate,
        limit: Int = 200,
    ): List<UiTask> = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        mutex.withLock {
            val database = db.readableDatabase
            ensureTasksMetaTable(database)
            val out = ArrayList<UiTask>()
            database.rawQuery(
                """
                SELECT t.doc_uri, t.doc_name, t.line_index, t.checked, t.task_id, t.title, t.due_epoch_ms
                FROM tasks_meta m
                JOIN tasks t ON t.task_id = m.task_id
                WHERE m.created_epoch_ms BETWEEN ? AND ?
                ORDER BY m.created_epoch_ms ASC, t.doc_name COLLATE NOCASE ASC, t.line_index ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(startMs.toString(), endMs.toString(), limit.toString()),
            ).use { cursor ->
                val docUriIdx = cursor.getColumnIndexOrThrow("doc_uri")
                val docNameIdx = cursor.getColumnIndexOrThrow("doc_name")
                val lineIdx = cursor.getColumnIndexOrThrow("line_index")
                val checkedIdx = cursor.getColumnIndexOrThrow("checked")
                val idIdx = cursor.getColumnIndexOrThrow("task_id")
                val titleIdx = cursor.getColumnIndexOrThrow("title")
                val dueIdx = cursor.getColumnIndexOrThrow("due_epoch_ms")
                while (cursor.moveToNext()) {
                    out +=
                        UiTask(
                            title = cursor.getString(titleIdx),
                            docUri = Uri.parse(cursor.getString(docUriIdx)),
                            docName = cursor.getString(docNameIdx),
                            lineIndex = cursor.getInt(lineIdx),
                            checked = cursor.getInt(checkedIdx) != 0,
                            taskId = cursor.getString(idIdx),
                            dueEpochMillis = if (cursor.isNull(dueIdx)) null else cursor.getLong(dueIdx),
                        )
                }
            }
            out
        }
    }

    suspend fun search(query: String, limit: Int = 50): List<SearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        mutex.withLock {
            val database = db.readableDatabase
            val out = ArrayList<SearchResult>()

            database.rawQuery(
                """
                SELECT uri, name, content
                FROM docs_fts
                WHERE docs_fts MATCH ?
                LIMIT ?
                """.trimIndent(),
                arrayOf(escapeFtsQuery(q), limit.toString()),
            ).use { cursor ->
                val uriIdx = cursor.getColumnIndexOrThrow("uri")
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val contentIdx = cursor.getColumnIndexOrThrow("content")
                val seen = HashSet<String>()
                while (cursor.moveToNext()) {
                    val uriStr = cursor.getString(uriIdx) ?: continue
                    if (!seen.add(uriStr)) continue
                    val content = cursor.getString(contentIdx) ?: ""
                    out += DocSearchResult(
                        title = cursor.getString(nameIdx),
                        uri = Uri.parse(uriStr),
                        snippet = buildSnippet(content, q),
                    )
                }
            }

            val remaining = (limit - out.size).coerceAtLeast(0)
            if (remaining == 0) return@withLock out

            database.rawQuery(
                """
                SELECT doc_uri, line_index, task_id, title
                FROM tasks_fts
                WHERE tasks_fts MATCH ?
                LIMIT ?
                """.trimIndent(),
                arrayOf(escapeFtsQuery(q), remaining.toString()),
            ).use { cursor ->
                val docUriIdx = cursor.getColumnIndexOrThrow("doc_uri")
                val lineIdx = cursor.getColumnIndexOrThrow("line_index")
                val idIdx = cursor.getColumnIndexOrThrow("task_id")
                val titleIdx = cursor.getColumnIndexOrThrow("title")
                while (cursor.moveToNext()) {
                    out += TaskSearchResult(
                        title = cursor.getString(titleIdx),
                        docUri = Uri.parse(cursor.getString(docUriIdx)),
                        lineIndex = cursor.getInt(lineIdx),
                        taskId = cursor.getString(idIdx),
                        dueEpochMillis = null,
                    )
                }
            }

            out
        }
    }

    suspend fun getTodayTasks(
        nowEpochMillis: Long = System.currentTimeMillis(),
        limit: Int = 200,
        status: TaskStatusFilter = TaskStatusFilter.Undone,
        tag: String? = null,
    ): List<UiTask> =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val today: LocalDate = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
            val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            mutex.withLock { queryDueTasks(start, end, limit, status = status, tag = tag) }
        }

    suspend fun getUpcomingTasks(
        nowEpochMillis: Long = System.currentTimeMillis(),
        limit: Int = 200,
        status: TaskStatusFilter = TaskStatusFilter.Undone,
        tag: String? = null,
    ): List<UiTask> =
        withContext(Dispatchers.IO) {
            val start = nowEpochMillis + DateUtils.MINUTE_IN_MILLIS
            val end = nowEpochMillis + DateUtils.DAY_IN_MILLIS * 30
            mutex.withLock { queryDueTasks(start, end, limit, status = status, tag = tag) }
        }

    suspend fun getAllTasks(
        limit: Int = 200,
        status: TaskStatusFilter = TaskStatusFilter.Undone,
        tag: String? = null,
    ): List<UiTask> =
        withContext(Dispatchers.IO) {
            mutex.withLock { queryAllTasks(limit = limit, status = status, tag = tag) }
        }

    suspend fun getDueTasksForReminder(
        nowEpochMillis: Long = System.currentTimeMillis(),
        windowMillis: Long = DateUtils.HOUR_IN_MILLIS,
        limit: Int = 50,
    ): List<UiTask> = withContext(Dispatchers.IO) {
        val end = nowEpochMillis + windowMillis
        mutex.withLock {
            val database = db.readableDatabase
            val out = ArrayList<UiTask>()
            database.rawQuery(
                """
                SELECT doc_uri, doc_name, line_index, checked, task_id, title, due_epoch_ms
                FROM tasks
                WHERE checked = 0
                  AND due_epoch_ms IS NOT NULL
                  AND due_epoch_ms BETWEEN ? AND ?
                ORDER BY due_epoch_ms ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(nowEpochMillis.toString(), end.toString(), limit.toString()),
            ).use { cursor ->
                val docUriIdx = cursor.getColumnIndexOrThrow("doc_uri")
                val docNameIdx = cursor.getColumnIndexOrThrow("doc_name")
                val lineIdx = cursor.getColumnIndexOrThrow("line_index")
                val checkedIdx = cursor.getColumnIndexOrThrow("checked")
                val idIdx = cursor.getColumnIndexOrThrow("task_id")
                val titleIdx = cursor.getColumnIndexOrThrow("title")
                val dueIdx = cursor.getColumnIndexOrThrow("due_epoch_ms")
                while (cursor.moveToNext()) {
                    out += UiTask(
                        title = cursor.getString(titleIdx),
                        docUri = Uri.parse(cursor.getString(docUriIdx)),
                        docName = cursor.getString(docNameIdx),
                        lineIndex = cursor.getInt(lineIdx),
                        checked = cursor.getInt(checkedIdx) != 0,
                        taskId = cursor.getString(idIdx),
                        dueEpochMillis = if (cursor.isNull(dueIdx)) null else cursor.getLong(dueIdx),
                    )
                }
            }
            out
        }
    }

    suspend fun wasReminderNotified(key: String, dueEpochMillis: Long): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            database.rawQuery(
                "SELECT value FROM meta WHERE key = ?",
                arrayOf("notified:$key"),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@withLock false
                cursor.getString(0) == dueEpochMillis.toString()
            }
        }
    }

    suspend fun markReminderNotified(key: String, dueEpochMillis: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.writableDatabase
            database.execSQL(
                "INSERT OR REPLACE INTO meta(key, value) VALUES(?, ?)",
                arrayOf<Any?>("notified:$key", dueEpochMillis.toString()),
            )
        }
    }

    suspend fun getRecentCompletedTasks(limit: Int = 50): List<UiTask> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            val out = ArrayList<UiTask>()
            database.rawQuery(
                """
                SELECT doc_uri, doc_name, line_index, checked, task_id, title, due_epoch_ms
                FROM tasks
                WHERE checked = 1
                ORDER BY COALESCE(done_epoch_ms, due_epoch_ms, 0) DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(limit.toString()),
            ).use { cursor ->
                val docUriIdx = cursor.getColumnIndexOrThrow("doc_uri")
                val docNameIdx = cursor.getColumnIndexOrThrow("doc_name")
                val lineIdx = cursor.getColumnIndexOrThrow("line_index")
                val checkedIdx = cursor.getColumnIndexOrThrow("checked")
                val idIdx = cursor.getColumnIndexOrThrow("task_id")
                val titleIdx = cursor.getColumnIndexOrThrow("title")
                val dueIdx = cursor.getColumnIndexOrThrow("due_epoch_ms")
                while (cursor.moveToNext()) {
                    out += UiTask(
                        title = cursor.getString(titleIdx),
                        docUri = Uri.parse(cursor.getString(docUriIdx)),
                        docName = cursor.getString(docNameIdx),
                        lineIndex = cursor.getInt(lineIdx),
                        checked = cursor.getInt(checkedIdx) != 0,
                        taskId = cursor.getString(idIdx),
                        dueEpochMillis = cursor.getLong(dueIdx),
                    )
                }
            }
            out
        }
    }

    private fun queryDueTasks(
        startMs: Long,
        endMs: Long,
        limit: Int,
        status: TaskStatusFilter,
        tag: String?,
    ): List<UiTask> {
        val database = db.readableDatabase
        val out = ArrayList<UiTask>()
        val statusClause =
            when (status) {
                TaskStatusFilter.All -> ""
                TaskStatusFilter.Undone -> " AND checked = 0"
                TaskStatusFilter.Done -> " AND checked = 1"
            }
        val tagClause = if (!tag.isNullOrBlank()) " AND tags LIKE ?" else ""
        val args = ArrayList<String>(4).apply {
            add(startMs.toString())
            add(endMs.toString())
            if (!tag.isNullOrBlank()) add("%${tag.trim().lowercase()}%")
            add(limit.toString())
        }.toTypedArray()
        database.rawQuery(
            """
            SELECT doc_uri, doc_name, line_index, checked, task_id, title, due_epoch_ms
            FROM tasks
            WHERE due_epoch_ms IS NOT NULL
              AND due_epoch_ms BETWEEN ? AND ?
              $statusClause
              $tagClause
            ORDER BY due_epoch_ms ASC
            LIMIT ?
            """.trimIndent(),
            args,
        ).use { cursor ->
            val docUriIdx = cursor.getColumnIndexOrThrow("doc_uri")
            val docNameIdx = cursor.getColumnIndexOrThrow("doc_name")
            val lineIdx = cursor.getColumnIndexOrThrow("line_index")
            val checkedIdx = cursor.getColumnIndexOrThrow("checked")
            val idIdx = cursor.getColumnIndexOrThrow("task_id")
            val titleIdx = cursor.getColumnIndexOrThrow("title")
            val dueIdx = cursor.getColumnIndexOrThrow("due_epoch_ms")
            while (cursor.moveToNext()) {
                out += UiTask(
                    title = cursor.getString(titleIdx),
                    docUri = Uri.parse(cursor.getString(docUriIdx)),
                    docName = cursor.getString(docNameIdx),
                    lineIndex = cursor.getInt(lineIdx),
                    checked = cursor.getInt(checkedIdx) != 0,
                    taskId = cursor.getString(idIdx),
                    dueEpochMillis = if (cursor.isNull(dueIdx)) null else cursor.getLong(dueIdx),
                )
            }
        }
        return out
    }

    private fun queryAllTasks(
        limit: Int,
        status: TaskStatusFilter,
        tag: String?,
    ): List<UiTask> {
        val database = db.readableDatabase
        val out = ArrayList<UiTask>()
        val statusClause =
            when (status) {
                TaskStatusFilter.All -> ""
                TaskStatusFilter.Undone -> " AND checked = 0"
                TaskStatusFilter.Done -> " AND checked = 1"
            }
        val tagClause = if (!tag.isNullOrBlank()) " AND tags LIKE ?" else ""
        val args = ArrayList<String>(2).apply {
            if (!tag.isNullOrBlank()) add("%${tag.trim().lowercase()}%")
            add(limit.toString())
        }.toTypedArray()
        database.rawQuery(
            """
            SELECT doc_uri, doc_name, line_index, checked, task_id, title, due_epoch_ms
            FROM tasks
            WHERE 1 = 1
              $statusClause
              $tagClause
            ORDER BY (due_epoch_ms IS NULL) ASC, due_epoch_ms ASC, doc_name COLLATE NOCASE ASC, line_index ASC
            LIMIT ?
            """.trimIndent(),
            args,
        ).use { cursor ->
            val docUriIdx = cursor.getColumnIndexOrThrow("doc_uri")
            val docNameIdx = cursor.getColumnIndexOrThrow("doc_name")
            val lineIdx = cursor.getColumnIndexOrThrow("line_index")
            val checkedIdx = cursor.getColumnIndexOrThrow("checked")
            val idIdx = cursor.getColumnIndexOrThrow("task_id")
            val titleIdx = cursor.getColumnIndexOrThrow("title")
            val dueIdx = cursor.getColumnIndexOrThrow("due_epoch_ms")
            while (cursor.moveToNext()) {
                out += UiTask(
                    title = cursor.getString(titleIdx),
                    docUri = Uri.parse(cursor.getString(docUriIdx)),
                    docName = cursor.getString(docNameIdx),
                    lineIndex = cursor.getInt(lineIdx),
                    checked = cursor.getInt(checkedIdx) != 0,
                    taskId = cursor.getString(idIdx),
                    dueEpochMillis = cursor.getLong(dueIdx),
                )
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

    private fun escapeFtsQuery(raw: String): String {
        val tokens = raw.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return "\"\""
        return tokens.joinToString(" ") { token ->
            val cleaned = token.replace("\"", "\"\"").replace("*", "")
            val isSimple = cleaned.isNotBlank() && cleaned.all { it.isLetterOrDigit() || it == '_' }
            if (isSimple) "$cleaned*" else "\"$cleaned\""
        }
    }

    private fun parseEpochMillis(text: String): Long? {
        return runCatching {
            val ldt = LocalDateTime.parse(text.trim(), dueFormatter)
            ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun parseDoneEpochMillis(text: String): Long? {
        return runCatching {
            val ldt = LocalDateTime.parse(text.trim(), doneFormatter)
            ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    suspend fun recordDailyDocEdited(
        docUri: String,
        day: LocalDate = LocalDate.now(),
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.writableDatabase
            ensureDailyContribTables(database)
            val dayStr = day.toString()

            val prevDay =
                database.rawQuery(
                    "SELECT day FROM doc_daily_edited WHERE doc_uri = ?",
                    arrayOf(docUri),
                ).use { cursor ->
                    if (!cursor.moveToFirst()) null else cursor.getString(0)
                }
            if (prevDay == dayStr) return@withContext

            database.execSQL(
                "INSERT OR REPLACE INTO doc_daily_edited(doc_uri, day) VALUES(?, ?)",
                arrayOf(docUri, dayStr),
            )
            database.execSQL("INSERT OR IGNORE INTO daily_contrib(day, docs_edited, tasks_done) VALUES(?, 0, 0)", arrayOf(dayStr))
            database.execSQL("UPDATE daily_contrib SET docs_edited = docs_edited + 1 WHERE day = ?", arrayOf(dayStr))
        }
    }

    suspend fun incrementDailyTasksDone(
        day: LocalDate = LocalDate.now(),
        delta: Int,
    ) = withContext(Dispatchers.IO) {
        if (delta <= 0) return@withContext
        mutex.withLock {
            val database = db.writableDatabase
            ensureDailyContribTables(database)
            val dayStr = day.toString()
            database.execSQL("INSERT OR IGNORE INTO daily_contrib(day, docs_edited, tasks_done) VALUES(?, 0, 0)", arrayOf(dayStr))
            database.execSQL("UPDATE daily_contrib SET tasks_done = tasks_done + ? WHERE day = ?", arrayOf(delta, dayStr))
        }
    }

    suspend fun getDailyContribSince(
        cutoff: LocalDate,
    ): Map<LocalDate, DailyContrib> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.writableDatabase
            ensureDailyContribTables(database)
            val out = HashMap<LocalDate, DailyContrib>()
            database.rawQuery(
                "SELECT day, docs_edited, tasks_done FROM daily_contrib WHERE day >= ?",
                arrayOf(cutoff.toString()),
            ).use { cursor ->
                val dayIdx = cursor.getColumnIndexOrThrow("day")
                val docsIdx = cursor.getColumnIndexOrThrow("docs_edited")
                val tasksIdx = cursor.getColumnIndexOrThrow("tasks_done")
                while (cursor.moveToNext()) {
                    val day = runCatching { LocalDate.parse(cursor.getString(dayIdx)) }.getOrNull() ?: continue
                    out[day] =
                        DailyContrib(
                            day = day,
                            docsEdited = cursor.getInt(docsIdx),
                            tasksDone = cursor.getInt(tasksIdx),
                        )
                }
            }
            out
        }
    }

    private fun ensureDailyContribTables(database: android.database.sqlite.SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_contrib (
              day TEXT PRIMARY KEY,
              docs_edited INTEGER NOT NULL DEFAULT 0,
              tasks_done INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS doc_daily_edited (
              doc_uri TEXT PRIMARY KEY,
              day TEXT NOT NULL
            );
            """.trimIndent(),
        )
    }

    private fun ensureDocsMetaTable(database: android.database.sqlite.SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS docs_meta (
              uri TEXT PRIMARY KEY,
              created_epoch_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )
    }

    private fun ensureTasksMetaTable(database: android.database.sqlite.SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks_meta (
              task_id TEXT PRIMARY KEY,
              doc_uri TEXT NOT NULL,
              created_epoch_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )
    }

    private fun resolveDocName(uriStr: String): String? {
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return null
        runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@runCatching null
                cursor.getString(0)
            }
        }.onSuccess { if (!it.isNullOrBlank()) return it }

        return runCatching { DocumentFile.fromSingleUri(appContext, uri)?.name }.getOrNull()
    }

    private fun computeDocBaseNameLocal(name: String): String =
        if (name.endsWith(".md", ignoreCase = true)) name.dropLast(3) else name

    private companion object {
        val mutex = Mutex()
    }
}
