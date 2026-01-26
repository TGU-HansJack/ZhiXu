package app.zhixu.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class VaultIndexDb(
    context: Context,
) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
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

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dir_index_meta (
              root_uri TEXT PRIMARY KEY,
              built_at_ms INTEGER NOT NULL
            );
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dir_index_entries (
              root_uri TEXT NOT NULL,
              relative_path TEXT NOT NULL,
              parent_path TEXT,
              name TEXT NOT NULL,
              uri TEXT,
              is_dir INTEGER NOT NULL,
              last_modified INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY (root_uri, relative_path)
            );
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_dir_index_parent ON dir_index_entries(root_uri, parent_path, is_dir, name COLLATE NOCASE);")
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

        if (oldVersion < 9) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS dir_index_meta (
                  root_uri TEXT PRIMARY KEY,
                  built_at_ms INTEGER NOT NULL
                );
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS dir_index_entries (
                  root_uri TEXT NOT NULL,
                  relative_path TEXT NOT NULL,
                  parent_path TEXT,
                  name TEXT NOT NULL,
                  uri TEXT,
                  is_dir INTEGER NOT NULL,
                  last_modified INTEGER NOT NULL DEFAULT 0,
                  PRIMARY KEY (root_uri, relative_path)
                );
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_dir_index_parent ON dir_index_entries(root_uri, parent_path, is_dir, name COLLATE NOCASE);")
        }
    }

    companion object {
        private const val DB_NAME = "vault_index.db"
        private const val DB_VERSION = 11
    }
}
