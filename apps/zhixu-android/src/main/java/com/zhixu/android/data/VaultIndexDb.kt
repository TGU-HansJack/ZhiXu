package com.zhixu.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class VaultIndexDb(
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
            CREATE VIRTUAL TABLE IF NOT EXISTS docs_fts
            USING fts4(
              uri,
              name,
              content,
              last_modified,
              size
              , tokenize=unicode61
            );
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS docs_meta (
              uri TEXT PRIMARY KEY,
              created_epoch_ms INTEGER NOT NULL
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
              due_epoch_ms INTEGER,
              done_epoch_ms INTEGER,
              raw_line TEXT NOT NULL,
              PRIMARY KEY (doc_uri, line_index)
            );
            """.trimIndent(),
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_due_checked ON tasks(due_epoch_ms, checked);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_doc ON tasks(doc_uri);")

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

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_contrib (
              day TEXT PRIMARY KEY,
              docs_edited INTEGER NOT NULL DEFAULT 0,
              tasks_done INTEGER NOT NULL DEFAULT 0
            );
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS doc_daily_edited (
              doc_uri TEXT PRIMARY KEY,
              day TEXT NOT NULL
            );
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 6) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_contrib (
                  day TEXT PRIMARY KEY,
                  docs_edited INTEGER NOT NULL DEFAULT 0,
                  tasks_done INTEGER NOT NULL DEFAULT 0
                );
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS doc_daily_edited (
                  doc_uri TEXT PRIMARY KEY,
                  day TEXT NOT NULL
                );
                """.trimIndent(),
            )
        }

        if (oldVersion < 7) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS docs_meta (
                  uri TEXT PRIMARY KEY,
                  created_epoch_ms INTEGER NOT NULL
                );
                """.trimIndent(),
            )
        }

        if (oldVersion < 8) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tasks_meta (
                  task_id TEXT PRIMARY KEY,
                  doc_uri TEXT NOT NULL,
                  created_epoch_ms INTEGER NOT NULL
                );
                """.trimIndent(),
            )
        }
    }

    companion object {
        private const val DB_NAME = "vault_index.db"
        private const val DB_VERSION = 8
    }
}
