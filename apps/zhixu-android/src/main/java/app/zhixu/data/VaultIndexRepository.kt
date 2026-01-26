package app.zhixu.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

class VaultIndexRepository(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val db = VaultIndexDb(context)

    data class IndexedDocMeta(
        val name: String,
        val lastModified: Long,
        val size: Long,
    )

    suspend fun hasAnyIndexedDocs(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            database.rawQuery("SELECT 1 FROM docs_fts LIMIT 1", null).use { cursor ->
                cursor.moveToFirst()
            }
        }
    }

    suspend fun listDocs(
        limit: Int = 2000,
    ): List<UiDoc> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, 50_000)
        mutex.withLock {
            val database = db.readableDatabase
            ensureDocsMetaTable(database)
            val out = ArrayList<UiDoc>()
            database.rawQuery(
                """
                SELECT f.uri, f.name, f.last_modified, f.size, COALESCE(m.created_epoch_ms, 0) AS created_epoch_ms
                FROM docs_fts f
                LEFT JOIN docs_meta m ON m.uri = f.uri
                ORDER BY CAST(f.last_modified AS INTEGER) DESC, f.name COLLATE NOCASE ASC
                LIMIT ?
                """.trimIndent(),
                arrayOf(safeLimit.toString()),
            ).use { cursor ->
                val uriIdx = cursor.getColumnIndexOrThrow("uri")
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val lastModifiedIdx = cursor.getColumnIndexOrThrow("last_modified")
                val sizeIdx = cursor.getColumnIndexOrThrow("size")
                val createdIdx = cursor.getColumnIndexOrThrow("created_epoch_ms")
                while (cursor.moveToNext()) {
                    val uriStr = cursor.getString(uriIdx).orEmpty()
                    val indexedName = cursor.getString(nameIdx).orEmpty()
                    val name =
                        indexedName.ifBlank {
                            resolveDocName(uriStr).orEmpty().ifBlank { "Document.md" }
                        }
                    val lastModified = cursor.getString(lastModifiedIdx).toLongOrNull() ?: 0L
                    val size = cursor.getString(sizeIdx).toLongOrNull() ?: 0L
                    val created = cursor.getLong(createdIdx)
                    out +=
                        UiDoc(
                            name = name,
                            uri = Uri.parse(uriStr),
                            lastModified = lastModified,
                            size = size,
                            baseName = computeDocBaseNameLocal(name),
                            createdAt = if (created > 0L) created else lastModified,
                            createdAtText = "",
                            editedAtText = "",
                        )
                }
            }
            out
        }
    }

    suspend fun listAllDocUris(): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            val out = ArrayList<String>()
            database.rawQuery("SELECT uri FROM docs_fts", null).use { cursor ->
                val uriIdx = cursor.getColumnIndexOrThrow("uri")
                while (cursor.moveToNext()) {
                    out += cursor.getString(uriIdx).orEmpty()
                }
            }
            out
        }
    }

    suspend fun listAllIndexedDocMeta(): Map<String, IndexedDocMeta> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val database = db.readableDatabase
            val out = HashMap<String, IndexedDocMeta>()
            database.rawQuery(
                """
                SELECT
                  uri,
                  COALESCE(name, '') AS name,
                  CAST(COALESCE(last_modified, '0') AS INTEGER) AS last_modified,
                  CAST(COALESCE(size, '0') AS INTEGER) AS size
                FROM docs_fts
                """.trimIndent(),
                null,
            ).use { cursor ->
                val uriIdx = cursor.getColumnIndexOrThrow("uri")
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val lastIdx = cursor.getColumnIndexOrThrow("last_modified")
                val sizeIdx = cursor.getColumnIndexOrThrow("size")
                while (cursor.moveToNext()) {
                    val uriStr = cursor.getString(uriIdx).orEmpty()
                    if (uriStr.isBlank()) continue
                    out[uriStr] =
                        IndexedDocMeta(
                            name = cursor.getString(nameIdx).orEmpty(),
                            lastModified = cursor.getLong(lastIdx),
                            size = cursor.getLong(sizeIdx),
                        )
                }
            }
            out
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
            runCatching {
                database.execSQL(
                    "UPDATE docs_fts SET name = ? WHERE uri = ?",
                    arrayOf<Any?>(name, uri),
                )
            }
        }
    }

    suspend fun getIndexedDocName(uriStr: String): String? = withContext(Dispatchers.IO) {
        if (uriStr.isBlank()) return@withContext null
        mutex.withLock {
            val database = db.readableDatabase
            database.rawQuery(
                "SELECT name FROM docs_fts WHERE uri = ? LIMIT 1",
                arrayOf(uriStr),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0)
            }?.takeIf { it.isNotBlank() }
        }
    }

    suspend fun deleteDocumentsByUriStrings(
        docUris: List<String>,
    ) = withContext(Dispatchers.IO) {
        if (docUris.isEmpty()) return@withContext
        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                ensureDocsMetaTable(database)
                val chunkSize = 800
                for (chunk in docUris.chunked(chunkSize)) {
                    val placeholders = chunk.joinToString(",") { "?" }
                    val args: Array<Any?> = chunk.map { it as Any? }.toTypedArray()
                    database.execSQL("DELETE FROM docs_fts WHERE uri IN ($placeholders)", args)
                    database.execSQL("DELETE FROM docs_meta WHERE uri IN ($placeholders)", args)
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
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
                ensureDocsMetaTable(database)
                ensureDailyContribTables(database)

                runCatching {
                    database.execSQL(
                        "UPDATE docs_fts SET uri = ?, name = ? WHERE uri = ?",
                        arrayOf<Any?>(newUri, newName, oldUri),
                    )
                }

                val createdEpochMs =
                    runCatching {
                        database.rawQuery(
                            "SELECT created_epoch_ms FROM docs_meta WHERE uri = ? LIMIT 1",
                            arrayOf(oldUri),
                        ).use { cursor ->
                            if (!cursor.moveToFirst()) 0L else cursor.getLong(0)
                        }
                    }.getOrDefault(0L)
                if (createdEpochMs > 0L) {
                    database.execSQL(
                        "INSERT OR IGNORE INTO docs_meta(uri, created_epoch_ms) VALUES(?, ?)",
                        arrayOf<Any?>(newUri, createdEpochMs),
                    )
                }
                database.execSQL("DELETE FROM docs_meta WHERE uri = ?", arrayOf(oldUri))

                val lastDay =
                    runCatching {
                        database.rawQuery(
                            "SELECT day FROM doc_daily_edited WHERE doc_uri = ? LIMIT 1",
                            arrayOf(oldUri),
                        ).use { cursor ->
                            if (!cursor.moveToFirst()) "" else cursor.getString(0).orEmpty()
                        }
                    }.getOrDefault("")
                if (lastDay.isNotBlank()) {
                    database.execSQL(
                        "INSERT OR IGNORE INTO doc_daily_edited(doc_uri, day) VALUES(?, ?)",
                        arrayOf<Any?>(newUri, lastDay),
                    )
                }
                database.execSQL("DELETE FROM doc_daily_edited WHERE doc_uri = ?", arrayOf(oldUri))

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
                    out +=
                        DocSearchResult(
                            title = cursor.getString(nameIdx),
                            uri = Uri.parse(uriStr),
                            snippet = buildSnippet(content, q),
                        )
                }
            }

            out
        }
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
        when {
            name.endsWith(".md", ignoreCase = true) -> name.dropLast(3)
            name.endsWith(".zhixu", ignoreCase = true) && name.length > ".zhixu".length -> name.dropLast(".zhixu".length)
            else -> name
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

    private companion object {
        val mutex = Mutex()
    }
}

