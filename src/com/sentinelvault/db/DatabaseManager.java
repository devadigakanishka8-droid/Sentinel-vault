package com.sentinelvault.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens connections to the SQLite database and creates the 6 tables on first run. */
public class DatabaseManager {

    // foreign_keys=on: SQLite ignores foreign keys unless this is set for every connection.
    private static final String URL = "jdbc:sqlite:sentinelvault.db?foreign_keys=on";

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    /** Safe to call every time the app starts: everything uses IF NOT EXISTS. */
    public static void initializeTables() throws SQLException {
        String[] statements = {
            // 1. users
            "CREATE TABLE IF NOT EXISTS users ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "username TEXT NOT NULL UNIQUE,"
            + "password_hash TEXT NOT NULL,"
            + "role TEXT NOT NULL DEFAULT 'VIEWER' CHECK (role IN ('OWNER','EDITOR','VIEWER')),"
            + "is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0,1)),"
            + "failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),"
            + "locked_until TEXT,"
            + "created_at TEXT NOT NULL DEFAULT (datetime('now')),"
            + "last_login TEXT)",

            // 2. sessions
            "CREATE TABLE IF NOT EXISTS sessions ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,"
            + "token_hash TEXT NOT NULL UNIQUE,"
            + "device TEXT,"
            + "created_at TEXT NOT NULL DEFAULT (datetime('now')),"
            + "expires_at TEXT NOT NULL,"
            + "last_seen TEXT,"
            + "ended_at TEXT,"
            + "end_reason TEXT CHECK (end_reason IN ('LOGOUT','EXPIRED','REVOKED','LOCKED')),"
            + "CHECK ((ended_at IS NULL) = (end_reason IS NULL)))",

            // 3. folders
            "CREATE TABLE IF NOT EXISTS folders ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "name TEXT NOT NULL CHECK (length(name) BETWEEN 1 AND 255),"
            + "parent_id INTEGER REFERENCES folders(id) ON DELETE RESTRICT,"
            + "owner_id INTEGER NOT NULL REFERENCES users(id),"
            + "created_at TEXT NOT NULL DEFAULT (datetime('now')),"
            + "is_deleted INTEGER NOT NULL DEFAULT 0 CHECK (is_deleted IN (0,1)),"
            + "CHECK (parent_id IS NULL OR parent_id <> id))",
            "CREATE UNIQUE INDEX IF NOT EXISTS ux_folders_name "
            + "ON folders (COALESCE(parent_id, 0), name) WHERE is_deleted = 0",

            // 4. files (the file as the user sees it)
            "CREATE TABLE IF NOT EXISTS files ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "filename TEXT NOT NULL CHECK (length(filename) BETWEEN 1 AND 255),"
            + "folder_id INTEGER REFERENCES folders(id) ON DELETE RESTRICT,"
            + "owner_id INTEGER NOT NULL REFERENCES users(id),"
            + "access_level TEXT NOT NULL DEFAULT 'VIEWER' CHECK (access_level IN ('OWNER','EDITOR','VIEWER')),"
            + "current_version INTEGER NOT NULL DEFAULT 1 CHECK (current_version >= 1),"
            + "is_deleted INTEGER NOT NULL DEFAULT 0 CHECK (is_deleted IN (0,1)),"
            + "deleted_at TEXT,"
            + "uploaded_at TEXT NOT NULL DEFAULT (datetime('now')),"
            + "CHECK ((is_deleted = 1) = (deleted_at IS NOT NULL)))",

            // 5. file_versions (encrypted data + its own key)
            "CREATE TABLE IF NOT EXISTS file_versions ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "file_id INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,"
            + "version_no INTEGER NOT NULL CHECK (version_no >= 1),"
            + "stored_path TEXT NOT NULL UNIQUE,"
            + "wrapped_key TEXT NOT NULL,"
            + "size_bytes INTEGER NOT NULL CHECK (size_bytes >= 0),"
            + "sha256 TEXT NOT NULL CHECK (length(sha256) = 64),"
            + "uploaded_by INTEGER NOT NULL REFERENCES users(id),"
            + "uploaded_at TEXT NOT NULL DEFAULT (datetime('now')),"
            + "comment TEXT,"
            + "UNIQUE (file_id, version_no))",

            // 6. audit_logs (append-only)
            "CREATE TABLE IF NOT EXISTS audit_logs ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
            + "user_id INTEGER NOT NULL REFERENCES users(id),"
            + "file_id INTEGER REFERENCES files(id),"
            + "session_id INTEGER REFERENCES sessions(id),"
            + "action TEXT NOT NULL,"
            + "status TEXT NOT NULL CHECK (status IN ('SUCCESS','DENIED','FAILED')),"
            + "details TEXT,"
            + "timestamp TEXT NOT NULL,"
            + "row_hash TEXT NOT NULL)",
            "CREATE TRIGGER IF NOT EXISTS audit_no_update BEFORE UPDATE ON audit_logs "
            + "BEGIN SELECT RAISE(ABORT, 'audit_logs is append-only'); END",
            "CREATE TRIGGER IF NOT EXISTS audit_no_delete BEFORE DELETE ON audit_logs "
            + "BEGIN SELECT RAISE(ABORT, 'audit_logs is append-only'); END",

            // indexes
            "CREATE INDEX IF NOT EXISTS idx_files_owner ON files (owner_id)",
            "CREATE INDEX IF NOT EXISTS idx_files_folder ON files (folder_id) WHERE is_deleted = 0",
            "CREATE INDEX IF NOT EXISTS idx_folders_parent ON folders (parent_id)",
            "CREATE INDEX IF NOT EXISTS idx_versions_file ON file_versions (file_id)",
            "CREATE INDEX IF NOT EXISTS idx_sessions_active ON sessions (user_id) WHERE ended_at IS NULL",
            "CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_logs (user_id)",
            "CREATE INDEX IF NOT EXISTS idx_audit_file ON audit_logs (file_id)",
            "CREATE INDEX IF NOT EXISTS idx_audit_time ON audit_logs (timestamp)"
        };
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            for (String sql : statements) stmt.execute(sql);
        }
    }
}
