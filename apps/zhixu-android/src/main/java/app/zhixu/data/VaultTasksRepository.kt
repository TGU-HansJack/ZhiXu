package app.zhixu.data

import android.content.Context
import android.net.Uri
import android.text.format.DateUtils
import app.zhixu.core.tasks.TaskSyntax
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class VaultTasksRepository(
    context: Context,
) {
    private val db = VaultTasksDb(context)
    private val dueFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val doneFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    enum class TaskStatusFilter { All, Undone, Done }

    data class IndexedDocMeta(
        val lastModified: Long,
        val size: Long,
    )

    suspend fun hasAnyIndexedDocs(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            database.rawQuery("SELECT 1 FROM tasks_doc_meta LIMIT 1", null).use { cursor ->
                cursor.moveToFirst()
            }
        }
    }

    suspend fun listAllIndexedDocMeta(): Map<String, IndexedDocMeta> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            val out = HashMap<String, IndexedDocMeta>()
            database.rawQuery(
                """
                SELECT
                  doc_uri,
                  CAST(COALESCE(last_modified, 0) AS INTEGER) AS last_modified,
                  CAST(COALESCE(size, 0) AS INTEGER) AS size
                FROM tasks_doc_meta
                """.trimIndent(),
                null,
            ).use { cursor ->
                val uriIdx = cursor.getColumnIndexOrThrow("doc_uri")
                val lastIdx = cursor.getColumnIndexOrThrow("last_modified")
                val sizeIdx = cursor.getColumnIndexOrThrow("size")
                while (cursor.moveToNext()) {
                    val uri = cursor.getString(uriIdx).orEmpty()
                    if (uri.isBlank()) continue
                    out[uri] =
                        IndexedDocMeta(
                            lastModified = cursor.getLong(lastIdx),
                            size = cursor.getLong(sizeIdx),
                        )
                }
            }
            out
        }
    }

    suspend fun deleteTasksByDocUriStrings(
        docUris: List<String>,
    ) = withContext(Dispatchers.IO) {
        if (docUris.isEmpty()) return@withContext
        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                val chunkSize = 800
                for (chunk in docUris.chunked(chunkSize)) {
                    val placeholders = chunk.joinToString(",") { "?" }
                    val args: Array<Any?> = chunk.map { it as Any? }.toTypedArray()
                    database.execSQL("DELETE FROM tasks WHERE doc_uri IN ($placeholders)", args)
                    database.execSQL("DELETE FROM tasks_fts WHERE doc_uri IN ($placeholders)", args)
                    database.execSQL("DELETE FROM tasks_meta WHERE doc_uri IN ($placeholders)", args)
                    database.execSQL("DELETE FROM tasks_doc_meta WHERE doc_uri IN ($placeholders)", args)
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun deleteTasksByDocUri(
        docUri: Uri,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                database.execSQL("DELETE FROM tasks WHERE doc_uri = ?", arrayOf(docUri.toString()))
                database.execSQL("DELETE FROM tasks_fts WHERE doc_uri = ?", arrayOf(docUri.toString()))
                database.execSQL("DELETE FROM tasks_meta WHERE doc_uri = ?", arrayOf(docUri.toString()))
                database.execSQL("DELETE FROM tasks_doc_meta WHERE doc_uri = ?", arrayOf(docUri.toString()))
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun indexTasksForDocument(
        doc: UiDoc,
        content: String,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                database.execSQL("DELETE FROM tasks WHERE doc_uri = ?", arrayOf(doc.uri.toString()))
                database.execSQL("DELETE FROM tasks_fts WHERE doc_uri = ?", arrayOf(doc.uri.toString()))

                val tasks = TaskSyntax.parseTasks(content)
                for (task in tasks) {
                    val dueMs = task.due?.let(::parseEpochMillis)
                    val remindMs = task.remind?.let(::parseEpochMillis)
                    val doneMs = task.done?.let(::parseDoneEpochMillis)
                    val tags = task.tags.joinToString(" ") { it.trim().lowercase() }.trim()
                    database.execSQL(
                        """
                        INSERT OR REPLACE INTO tasks(
                          doc_uri, doc_name, line_index, checked, task_id, title, tags, priority, due_epoch_ms, remind_epoch_ms, remind_persist, done_epoch_ms, raw_line
                        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            doc.uri.toString(),
                            doc.name,
                            task.lineIndex,
                            if (task.checked) 1 else 0,
                            task.id,
                            task.title,
                            tags,
                            task.priority,
                            dueMs,
                            remindMs,
                            if (task.remindPersistent) 1 else 0,
                            doneMs,
                            task.rawLine,
                        ),
                    )
                    database.execSQL(
                        "INSERT INTO tasks_fts(doc_uri, line_index, task_id, title, tags, raw_line) VALUES(?,?,?,?,?,?)",
                        arrayOf<Any?>(
                            doc.uri.toString(),
                            task.lineIndex,
                            task.id,
                            task.title,
                            tags,
                            task.rawLine,
                        ),
                    )
                }

                database.execSQL(
                    "INSERT OR REPLACE INTO tasks_doc_meta(doc_uri, last_modified, size, indexed_at_ms) VALUES(?, ?, ?, ?)",
                    arrayOf<Any?>(doc.uri.toString(), doc.lastModified, doc.size, System.currentTimeMillis()),
                )

                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun updateDocName(
        docUri: String,
        newName: String,
    ) = withContext(Dispatchers.IO) {
        val uri = docUri.trim()
        val name = newName.trim()
        if (uri.isBlank() || name.isBlank()) return@withContext

        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                runCatching {
                    database.execSQL(
                        "UPDATE tasks SET doc_name = ? WHERE doc_uri = ?",
                        arrayOf<Any?>(name, uri),
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun migrateDocUri(
        oldUri: String,
        newUri: String,
        newName: String,
    ) = withContext(Dispatchers.IO) {
        if (oldUri.isBlank() || newUri.isBlank() || oldUri == newUri) return@withContext

        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                runCatching {
                    database.execSQL(
                        "UPDATE tasks SET doc_uri = ?, doc_name = ? WHERE doc_uri = ?",
                        arrayOf<Any?>(newUri, newName, oldUri),
                    )
                }
                runCatching {
                    database.execSQL(
                        "UPDATE tasks_fts SET doc_uri = ? WHERE doc_uri = ?",
                        arrayOf<Any?>(newUri, oldUri),
                    )
                }
                runCatching {
                    database.execSQL(
                        "UPDATE tasks_meta SET doc_uri = ? WHERE doc_uri = ?",
                        arrayOf<Any?>(newUri, oldUri),
                    )
                }
                runCatching {
                    database.execSQL(
                        "UPDATE tasks_doc_meta SET doc_uri = ? WHERE doc_uri = ?",
                        arrayOf<Any?>(newUri, oldUri),
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
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
            val out = ArrayList<UiTask>()
            database.rawQuery(
                """
                SELECT t.doc_uri, t.doc_name, t.line_index, t.checked, t.task_id, t.title, t.due_epoch_ms, t.remind_epoch_ms, t.remind_persist, t.priority
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
                val remindIdx = cursor.getColumnIndexOrThrow("remind_epoch_ms")
                val remindPersistIdx = cursor.getColumnIndexOrThrow("remind_persist")
                val priorityIdx = cursor.getColumnIndexOrThrow("priority")
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
                            remindEpochMillis = if (cursor.isNull(remindIdx)) null else cursor.getLong(remindIdx),
                            remindPersistent = cursor.getInt(remindPersistIdx) != 0,
                            priority = if (cursor.isNull(priorityIdx)) null else cursor.getInt(priorityIdx),
                        )
                }
            }
            out
        }
    }

    suspend fun search(query: String, limit: Int = 50): List<TaskSearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        mutex.withLock {
            val database = db.readableDatabase
            val out = ArrayList<TaskSearchResult>()
            database.rawQuery(
                """
                SELECT doc_uri, line_index, task_id, title
                FROM tasks_fts
                WHERE tasks_fts MATCH ?
                LIMIT ?
                """.trimIndent(),
                arrayOf(escapeFtsQuery(q), limit.toString()),
            ).use { cursor ->
                val docUriIdx = cursor.getColumnIndexOrThrow("doc_uri")
                val lineIdx = cursor.getColumnIndexOrThrow("line_index")
                val idIdx = cursor.getColumnIndexOrThrow("task_id")
                val titleIdx = cursor.getColumnIndexOrThrow("title")
                while (cursor.moveToNext()) {
                    out +=
                        TaskSearchResult(
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

    suspend fun getTasksDueOn(
        day: LocalDate,
        limit: Int = 200,
        status: TaskStatusFilter = TaskStatusFilter.Undone,
        tag: String? = null,
    ): List<UiTask> =
        withContext(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
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
                SELECT doc_uri, doc_name, line_index, checked, task_id, title, due_epoch_ms, remind_epoch_ms, remind_persist, priority
                FROM tasks
                WHERE checked = 0
                  AND (
                    (remind_epoch_ms IS NOT NULL AND (
                      remind_epoch_ms BETWEEN ? AND ?
                      OR (remind_persist = 1 AND remind_epoch_ms <= ?)
                    ))
                    OR (remind_epoch_ms IS NULL AND due_epoch_ms IS NOT NULL AND due_epoch_ms BETWEEN ? AND ?)
                  )
                ORDER BY (priority IS NULL) ASC, priority ASC, COALESCE(remind_epoch_ms, due_epoch_ms) ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(
                    nowEpochMillis.toString(),
                    end.toString(),
                    nowEpochMillis.toString(),
                    nowEpochMillis.toString(),
                    end.toString(),
                    limit.toString(),
                ),
            ).use { cursor ->
                val docUriIdx = cursor.getColumnIndexOrThrow("doc_uri")
                val docNameIdx = cursor.getColumnIndexOrThrow("doc_name")
                val lineIdx = cursor.getColumnIndexOrThrow("line_index")
                val checkedIdx = cursor.getColumnIndexOrThrow("checked")
                val idIdx = cursor.getColumnIndexOrThrow("task_id")
                val titleIdx = cursor.getColumnIndexOrThrow("title")
                val dueIdx = cursor.getColumnIndexOrThrow("due_epoch_ms")
                val remindIdx = cursor.getColumnIndexOrThrow("remind_epoch_ms")
                val remindPersistIdx = cursor.getColumnIndexOrThrow("remind_persist")
                val priorityIdx = cursor.getColumnIndexOrThrow("priority")
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
                            remindEpochMillis = if (cursor.isNull(remindIdx)) null else cursor.getLong(remindIdx),
                            remindPersistent = cursor.getInt(remindPersistIdx) != 0,
                            priority = if (cursor.isNull(priorityIdx)) null else cursor.getInt(priorityIdx),
                        )
                }
            }
            out
        }
    }

    suspend fun wasReminderNotified(key: String, triggerEpochMillis: Long): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            database.rawQuery(
                "SELECT value FROM meta WHERE key = ?",
                arrayOf("notified:$key"),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@withLock false
                cursor.getString(0) == triggerEpochMillis.toString()
            }
        }
    }

    suspend fun markReminderNotified(key: String, triggerEpochMillis: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.writableDatabase
            database.execSQL(
                "INSERT OR REPLACE INTO meta(key, value) VALUES(?, ?)",
                arrayOf<Any?>("notified:$key", triggerEpochMillis.toString()),
            )
        }
    }

    suspend fun getRecentCompletedTasks(limit: Int = 50): List<UiTask> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            val out = ArrayList<UiTask>()
            database.rawQuery(
                """
                SELECT doc_uri, doc_name, line_index, checked, task_id, title, due_epoch_ms, remind_epoch_ms, remind_persist, priority
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
                val remindIdx = cursor.getColumnIndexOrThrow("remind_epoch_ms")
                val remindPersistIdx = cursor.getColumnIndexOrThrow("remind_persist")
                val priorityIdx = cursor.getColumnIndexOrThrow("priority")
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
                            remindEpochMillis = if (cursor.isNull(remindIdx)) null else cursor.getLong(remindIdx),
                            remindPersistent = cursor.getInt(remindPersistIdx) != 0,
                            priority = if (cursor.isNull(priorityIdx)) null else cursor.getInt(priorityIdx),
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
            SELECT doc_uri, doc_name, line_index, checked, task_id, title, due_epoch_ms, remind_epoch_ms, remind_persist, priority
            FROM tasks
            WHERE due_epoch_ms IS NOT NULL
              AND due_epoch_ms BETWEEN ? AND ?
              $statusClause
              $tagClause
            ORDER BY (priority IS NULL) ASC, priority ASC, due_epoch_ms ASC
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
            val remindIdx = cursor.getColumnIndexOrThrow("remind_epoch_ms")
            val remindPersistIdx = cursor.getColumnIndexOrThrow("remind_persist")
            val priorityIdx = cursor.getColumnIndexOrThrow("priority")
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
                        remindEpochMillis = if (cursor.isNull(remindIdx)) null else cursor.getLong(remindIdx),
                        remindPersistent = cursor.getInt(remindPersistIdx) != 0,
                        priority = if (cursor.isNull(priorityIdx)) null else cursor.getInt(priorityIdx),
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
            SELECT doc_uri, doc_name, line_index, checked, task_id, title, due_epoch_ms, remind_epoch_ms, remind_persist, priority
            FROM tasks
            WHERE 1 = 1
              $statusClause
              $tagClause
            ORDER BY (priority IS NULL) ASC, priority ASC, (due_epoch_ms IS NULL) ASC, due_epoch_ms ASC, doc_name COLLATE NOCASE ASC, line_index ASC
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
            val remindIdx = cursor.getColumnIndexOrThrow("remind_epoch_ms")
            val remindPersistIdx = cursor.getColumnIndexOrThrow("remind_persist")
            val priorityIdx = cursor.getColumnIndexOrThrow("priority")
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
                        remindEpochMillis = if (cursor.isNull(remindIdx)) null else cursor.getLong(remindIdx),
                        remindPersistent = cursor.getInt(remindPersistIdx) != 0,
                        priority = if (cursor.isNull(priorityIdx)) null else cursor.getInt(priorityIdx),
                    )
            }
        }
        return out
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

    private companion object {
        val mutex = Mutex()
    }
}
