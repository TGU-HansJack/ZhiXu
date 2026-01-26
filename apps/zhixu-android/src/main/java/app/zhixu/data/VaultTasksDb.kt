package app.zhixu.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class VaultTasksDb(
    context: Context,
) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            );
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks_doc_meta (
              doc_uri TEXT PRIMARY KEY,
              last_modified INTEGER NOT NULL DEFAULT 0,
              size INTEGER NOT NULL DEFAULT 0,
              indexed_at_ms INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks_meta (
              task_id TEXT PRIMARY KEY,
              doc_uri TEXT NOT NULL,
              created_epoch_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS tasks (
              doc_uri TEXT NOT NULL,
              doc_name TEXT NOT NULL,
              line_index INTEGER NOT NULL,
              checked INTEGER NOT NULL,
              task_id TEXT,
              title TEXT NOT NULL,
              tags TEXT NOT NULL DEFAULT '',
              priority INTEGER,
              due_epoch_ms INTEGER,
              remind_epoch_ms INTEGER,
              remind_persist INTEGER NOT NULL DEFAULT 0,
              done_epoch_ms INTEGER,
              raw_line TEXT NOT NULL,
              PRIMARY KEY (doc_uri, line_index)
            );
            """.trimIndent(),
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_due_checked ON tasks(due_epoch_ms, checked);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_remind_checked ON tasks(remind_epoch_ms, checked);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_doc ON tasks(doc_uri);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_priority_due ON tasks(priority, due_epoch_ms);")

        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS tasks_fts
            USING fts4(
              doc_uri,
              line_index,
              task_id,
              title,
              tags,
              raw_line
              , tokenize=unicode61
            );
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Initial schema is version 1. Future migrations should be additive.
        if (oldVersion < 1) {
            onCreate(db)
            return
        }

        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tasks_doc_meta (
                  doc_uri TEXT PRIMARY KEY,
                  last_modified INTEGER NOT NULL DEFAULT 0,
                  size INTEGER NOT NULL DEFAULT 0,
                  indexed_at_ms INTEGER NOT NULL DEFAULT 0
                );
                """.trimIndent(),
            )
        }
    }

    companion object {
        private const val DB_NAME = "tasks.db"
        private const val DB_VERSION = 2
    }
}
