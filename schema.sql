-- =====================================================================
--  SentinelVault database schema  (SQLite 3)
--  6 tables: users, sessions, folders, files, file_versions, audit_logs
--  Run once on a NEW database. Safe to re-run (IF NOT EXISTS).
-- =====================================================================

-- SQLite does NOT enforce foreign keys unless this is switched on for every connection.
PRAGMA foreign_keys = ON;

-- ---------------------------------------------------------------------
-- 1. USERS  - one row per account
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    username        TEXT    NOT NULL UNIQUE,
    password_hash   TEXT    NOT NULL,                       -- BCrypt hash, never the password
    role            TEXT    NOT NULL DEFAULT 'VIEWER'
                            CHECK (role IN ('OWNER', 'EDITOR', 'VIEWER')),
    is_active       INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),   -- 0 = account disabled
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),  -- wrong passwords in a row
    locked_until    TEXT,                                   -- NULL = not locked
    created_at      TEXT    NOT NULL DEFAULT (datetime('now')),
    last_login      TEXT
);

-- ---------------------------------------------------------------------
-- 2. SESSIONS  - one row per login, so we can see who is logged in and end a session
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sessions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  TEXT    NOT NULL UNIQUE,                    -- SHA-256 of the session token (the token itself is never stored)
    device      TEXT,                                       -- e.g. computer name
    created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    expires_at  TEXT    NOT NULL,
    last_seen   TEXT,
    ended_at    TEXT,                                       -- NULL while the session is still active
    end_reason  TEXT    CHECK (end_reason IN ('LOGOUT', 'EXPIRED', 'REVOKED', 'LOCKED')),
    CHECK ((ended_at IS NULL) = (end_reason IS NULL))       -- an ended session must say why
);

-- ---------------------------------------------------------------------
-- 3. FOLDERS  - a tree: parent_id points at the folder this one sits in (NULL = top level)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS folders (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL CHECK (length(name) BETWEEN 1 AND 255),
    parent_id   INTEGER REFERENCES folders(id) ON DELETE RESTRICT,
    owner_id    INTEGER NOT NULL REFERENCES users(id),
    created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    is_deleted  INTEGER NOT NULL DEFAULT 0 CHECK (is_deleted IN (0, 1)),
    CHECK (parent_id IS NULL OR parent_id <> id)            -- a folder cannot contain itself
);
-- No two live folders with the same name in the same place (COALESCE because NULL parents compare as different)
CREATE UNIQUE INDEX IF NOT EXISTS ux_folders_name
    ON folders (COALESCE(parent_id, 0), name) WHERE is_deleted = 0;

-- ---------------------------------------------------------------------
-- 4. FILES  - the file as the user sees it. One access level per file.
--    The actual encrypted bytes and their key live in FILE_VERSIONS.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS files (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    filename        TEXT    NOT NULL CHECK (length(filename) BETWEEN 1 AND 255),
    folder_id       INTEGER REFERENCES folders(id) ON DELETE RESTRICT,   -- NULL = top level
    owner_id        INTEGER NOT NULL REFERENCES users(id),
    access_level    TEXT    NOT NULL DEFAULT 'VIEWER'                    -- lowest role allowed to open it
                            CHECK (access_level IN ('OWNER', 'EDITOR', 'VIEWER')),
    current_version INTEGER NOT NULL DEFAULT 1 CHECK (current_version >= 1),
    is_deleted      INTEGER NOT NULL DEFAULT 0 CHECK (is_deleted IN (0, 1)),  -- 1 = in the trash
    deleted_at      TEXT,
    uploaded_at     TEXT    NOT NULL DEFAULT (datetime('now')),
    CHECK ((is_deleted = 1) = (deleted_at IS NOT NULL))
);

-- ---------------------------------------------------------------------
-- 5. FILE_VERSIONS  - every upload of a file is a version; each has its OWN random key.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS file_versions (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    file_id      INTEGER NOT NULL REFERENCES files(id) ON DELETE CASCADE,
    version_no   INTEGER NOT NULL CHECK (version_no >= 1),
    stored_path  TEXT    NOT NULL UNIQUE,                   -- random name of the encrypted file on disk
    wrapped_key  TEXT    NOT NULL,                          -- file key encrypted under the master key; '' once destroyed
    size_bytes   INTEGER NOT NULL CHECK (size_bytes >= 0),
    sha256       TEXT    NOT NULL CHECK (length(sha256) = 64),  -- hash of the ORIGINAL content, proves it is unchanged
    uploaded_by  INTEGER NOT NULL REFERENCES users(id),
    uploaded_at  TEXT    NOT NULL DEFAULT (datetime('now')),
    comment      TEXT,
    UNIQUE (file_id, version_no)
);

-- ---------------------------------------------------------------------
-- 6. AUDIT_LOGS  - append-only record of who did what, when, and whether it was allowed
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER NOT NULL REFERENCES users(id),
    file_id     INTEGER REFERENCES files(id),               -- NULL for actions that are not about a file
    session_id  INTEGER REFERENCES sessions(id),
    action      TEXT    NOT NULL,                           -- LOGIN, UPLOAD, DOWNLOAD, DELETE, CHANGE_ROLE ...
    status      TEXT    NOT NULL CHECK (status IN ('SUCCESS', 'DENIED', 'FAILED')),
    details     TEXT,                                       -- what changed, e.g. 'role VIEWER -> EDITOR'
    timestamp   TEXT    NOT NULL,
    row_hash    TEXT    NOT NULL                            -- SHA-256 chain: detects edited or removed rows
);

-- Nobody may edit or delete audit rows, not even by mistake.
-- (Someone who can open the .db file with another tool could drop this trigger,
--  which is why row_hash is ALSO kept: a broken chain still exposes the change.)
CREATE TRIGGER IF NOT EXISTS audit_no_update BEFORE UPDATE ON audit_logs
BEGIN SELECT RAISE(ABORT, 'audit_logs is append-only'); END;
CREATE TRIGGER IF NOT EXISTS audit_no_delete BEFORE DELETE ON audit_logs
BEGIN SELECT RAISE(ABORT, 'audit_logs is append-only'); END;

-- ---------------------------------------------------------------------
-- Indexes: speed up the queries the app runs all the time
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_files_owner     ON files (owner_id);
CREATE INDEX IF NOT EXISTS idx_files_folder    ON files (folder_id) WHERE is_deleted = 0;
CREATE INDEX IF NOT EXISTS idx_folders_parent  ON folders (parent_id);
CREATE INDEX IF NOT EXISTS idx_versions_file   ON file_versions (file_id);
CREATE INDEX IF NOT EXISTS idx_sessions_active ON sessions (user_id) WHERE ended_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_audit_user      ON audit_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_file      ON audit_logs (file_id);
CREATE INDEX IF NOT EXISTS idx_audit_time      ON audit_logs (timestamp);

-- ---------------------------------------------------------------------
-- View for the Audit Log screen: names instead of ids, newest first
-- ---------------------------------------------------------------------
CREATE VIEW IF NOT EXISTS audit_view AS
SELECT a.id, a.timestamp, u.username, a.action, COALESCE(f.filename, '-') AS filename,
       a.status, a.details
FROM audit_logs a
JOIN users u       ON u.id = a.user_id
LEFT JOIN files f  ON f.id = a.file_id
ORDER BY a.id DESC;
