package app.zhixu.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class VaultDirIndexRepository(
    context: Context,
) {
    private val db = VaultIndexDb(context)
    private val mutex = Mutex()

    suspend fun hasAnyEntries(rootUri: String): Boolean = withContext(Dispatchers.IO) {
        if (rootUri.isBlank()) return@withContext false
        mutex.withLock {
            val database = db.readableDatabase
            database.rawQuery(
                "SELECT 1 FROM dir_index_entries WHERE root_uri = ? LIMIT 1",
                arrayOf(rootUri),
            ).use { cursor -> cursor.moveToFirst() }
        }
    }

    suspend fun getBuiltAtMs(rootUri: String): Long = withContext(Dispatchers.IO) {
        if (rootUri.isBlank()) return@withContext 0L
        mutex.withLock {
            val database = db.readableDatabase
            database.rawQuery(
                "SELECT built_at_ms FROM dir_index_meta WHERE root_uri = ? LIMIT 1",
                arrayOf(rootUri),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@use 0L
                val idx = cursor.getColumnIndex("built_at_ms")
                if (idx < 0 || cursor.isNull(idx)) 0L else cursor.getLong(idx)
            }
        }
    }

    suspend fun listChildren(
        rootUri: String,
        parentPath: String?,
    ): List<VaultTreeEntry> = withContext(Dispatchers.IO) {
        if (rootUri.isBlank()) return@withContext emptyList()
        val parentKey = parentPath?.takeIf { it.isNotBlank() }
        val depth = parentKey?.count { it == '/' } ?: 0

        mutex.withLock {
            val database = db.readableDatabase
            val out = ArrayList<VaultTreeEntry>(64)
            val (sql, args) =
                if (parentKey == null) {
                    Pair(
                        """
                        SELECT relative_path, name, uri, is_dir, parent_path, last_modified
                        FROM dir_index_entries
                        WHERE root_uri = ? AND parent_path IS NULL
                        ORDER BY is_dir DESC, name COLLATE NOCASE ASC
                        """.trimIndent(),
                        arrayOf(rootUri),
                    )
                } else {
                    Pair(
                        """
                        SELECT relative_path, name, uri, is_dir, parent_path, last_modified
                        FROM dir_index_entries
                        WHERE root_uri = ? AND parent_path = ?
                        ORDER BY is_dir DESC, name COLLATE NOCASE ASC
                        """.trimIndent(),
                        arrayOf(rootUri, parentKey),
                    )
                }

            database.rawQuery(sql, args).use { cursor ->
                val relIdx = cursor.getColumnIndexOrThrow("relative_path")
                val nameIdx = cursor.getColumnIndexOrThrow("name")
                val uriIdx = cursor.getColumnIndexOrThrow("uri")
                val dirIdx = cursor.getColumnIndexOrThrow("is_dir")
                val parentIdx = cursor.getColumnIndexOrThrow("parent_path")
                val lastIdx = cursor.getColumnIndexOrThrow("last_modified")
                while (cursor.moveToNext()) {
                    val rel = cursor.getString(relIdx).orEmpty()
                    val name = cursor.getString(nameIdx).orEmpty()
                    val uriStr = if (cursor.isNull(uriIdx)) null else cursor.getString(uriIdx)
                    val isDir = cursor.getInt(dirIdx) != 0
                    val parent = if (cursor.isNull(parentIdx)) null else cursor.getString(parentIdx)
                    val lastModified = if (cursor.isNull(lastIdx)) 0L else cursor.getLong(lastIdx)
                    out +=
                        VaultTreeEntry(
                            relativePath = rel,
                            name = name,
                            uri = uriStr?.let(android.net.Uri::parse),
                            isDirectory = isDir,
                            parentPath = parent,
                            depth = depth,
                            lastModified = lastModified,
                        )
                }
            }
            out
        }
    }

    internal data class DirIndexEntry(
        val relativePath: String,
        val parentPath: String?,
        val name: String,
        val uri: String?,
        val isDirectory: Boolean,
        val lastModified: Long,
    )

    suspend fun upsertEntry(
        rootUri: String,
        entry: DirIndexEntry,
        builtAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        if (rootUri.isBlank()) return@withContext
        val rel = entry.relativePath.trim().replace('\\', '/').trimStart('/')
        if (rel.isBlank()) return@withContext

        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                database.execSQL(
                    """
                    INSERT OR REPLACE INTO dir_index_entries(
                      root_uri, relative_path, parent_path, name, uri, is_dir, last_modified
                    ) VALUES(?,?,?,?,?,?,?)
                    """.trimIndent(),
                    arrayOf<Any?>(
                        rootUri,
                        rel,
                        entry.parentPath?.takeIf { it.isNotBlank() },
                        entry.name,
                        entry.uri,
                        if (entry.isDirectory) 1 else 0,
                        entry.lastModified,
                    ),
                )
                database.execSQL(
                    "INSERT OR REPLACE INTO dir_index_meta(root_uri, built_at_ms) VALUES(?, ?)",
                    arrayOf<Any?>(rootUri, builtAtMs),
                )
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun replaceAll(
        rootUri: String,
        entries: List<DirIndexEntry>,
        builtAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        if (rootUri.isBlank()) return@withContext
        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                database.execSQL("DELETE FROM dir_index_entries WHERE root_uri = ?", arrayOf(rootUri))
                database.execSQL(
                    "INSERT OR REPLACE INTO dir_index_meta(root_uri, built_at_ms) VALUES(?, ?)",
                    arrayOf<Any?>(rootUri, builtAtMs),
                )

                for (e in entries) {
                    database.execSQL(
                        """
                        INSERT OR REPLACE INTO dir_index_entries(
                          root_uri, relative_path, parent_path, name, uri, is_dir, last_modified
                        ) VALUES(?,?,?,?,?,?,?)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            rootUri,
                            e.relativePath,
                            e.parentPath,
                            e.name,
                            e.uri,
                            if (e.isDirectory) 1 else 0,
                            e.lastModified,
                        ),
                    )
                }

                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    suspend fun replaceChildren(
        rootUri: String,
        parentPath: String?,
        children: List<DirIndexEntry>,
        builtAtMs: Long,
    ) = withContext(Dispatchers.IO) {
        if (rootUri.isBlank()) return@withContext
        val parentKey = parentPath?.takeIf { it.isNotBlank() }

        mutex.withLock {
            val database = db.writableDatabase
            database.beginTransaction()
            try {
                val existingDirChildren = HashSet<String>()
                val existingChildren = HashSet<String>()
                val (querySql, queryArgs) =
                    if (parentKey == null) {
                        Pair(
                            "SELECT relative_path, is_dir FROM dir_index_entries WHERE root_uri = ? AND parent_path IS NULL",
                            arrayOf(rootUri),
                        )
                    } else {
                        Pair(
                            "SELECT relative_path, is_dir FROM dir_index_entries WHERE root_uri = ? AND parent_path = ?",
                            arrayOf(rootUri, parentKey),
                        )
                    }
                database.rawQuery(querySql, queryArgs).use { cursor ->
                    val relIdx = cursor.getColumnIndexOrThrow("relative_path")
                    val dirIdx = cursor.getColumnIndexOrThrow("is_dir")
                    while (cursor.moveToNext()) {
                        val rel = cursor.getString(relIdx).orEmpty()
                        existingChildren += rel
                        if (cursor.getInt(dirIdx) != 0) existingDirChildren += rel
                    }
                }

                // Remove old direct children first.
                if (parentKey == null) {
                    database.execSQL(
                        "DELETE FROM dir_index_entries WHERE root_uri = ? AND parent_path IS NULL",
                        arrayOf<Any?>(rootUri),
                    )
                } else {
                    database.execSQL(
                        "DELETE FROM dir_index_entries WHERE root_uri = ? AND parent_path = ?",
                        arrayOf<Any?>(rootUri, parentKey),
                    )
                }

                // Prune subtrees for removed directory children.
                val nextDirChildren = children.filter { it.isDirectory }.map { it.relativePath }.toSet()
                val removedDirs = existingDirChildren - nextDirChildren
                for (removed in removedDirs) {
                    database.execSQL(
                        "DELETE FROM dir_index_entries WHERE root_uri = ? AND (relative_path = ? OR relative_path LIKE ?)",
                        arrayOf<Any?>(rootUri, removed, "$removed%"),
                    )
                }

                for (e in children) {
                    database.execSQL(
                        """
                        INSERT OR REPLACE INTO dir_index_entries(
                          root_uri, relative_path, parent_path, name, uri, is_dir, last_modified
                        ) VALUES(?,?,?,?,?,?,?)
                        """.trimIndent(),
                        arrayOf<Any?>(
                            rootUri,
                            e.relativePath,
                            e.parentPath,
                            e.name,
                            e.uri,
                            if (e.isDirectory) 1 else 0,
                            e.lastModified,
                        ),
                    )
                }

                database.execSQL(
                    "INSERT OR REPLACE INTO dir_index_meta(root_uri, built_at_ms) VALUES(?, ?)",
                    arrayOf<Any?>(rootUri, builtAtMs),
                )

                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }
}
