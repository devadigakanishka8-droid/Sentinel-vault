package com.sentinelvault.db;

import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.VersionEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * File records live in two tables: files (what the user sees) and file_versions
 * (the encrypted copy, its wrapped key, size and hash). Deleting is "soft".
 */
public class FileDAO {

    // Joins a file with its CURRENT version.
    private static final String SELECT_CURRENT =
            "SELECT f.id, f.filename, f.owner_id, f.access_level, f.uploaded_at, f.folder_id, "
            + "f.current_version, v.stored_path "
            + "FROM files f JOIN file_versions v ON v.file_id = f.id AND v.version_no = f.current_version ";

    /** Saves a new file (version 1) in one transaction and returns its id. */
    public int addFile(String filename, int ownerId, Role accessLevel, String wrappedKey,
                       String storedPath, long sizeBytes, String sha256) throws SQLException {
        try (Connection conn = DatabaseManager.connect()) {
            conn.setAutoCommit(false);
            try {
                int fileId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO files(filename, owner_id, access_level) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, filename);
                    ps.setInt(2, ownerId);
                    ps.setString(3, accessLevel.name());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Could not get the new file id");
                        fileId = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO file_versions(file_id, version_no, stored_path, wrapped_key, "
                        + "size_bytes, sha256, uploaded_by) VALUES (?, 1, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, fileId);
                    ps.setString(2, storedPath);
                    ps.setString(3, wrappedKey);
                    ps.setLong(4, sizeBytes);
                    ps.setString(5, sha256);
                    ps.setInt(6, ownerId);
                    ps.executeUpdate();
                }
                conn.commit();
                return fileId;
            } catch (SQLException e) {
                conn.rollback();   // never leave a file without its version
                throw e;
            }
        }
    }

    public FileEntry getFile(int fileId) throws SQLException {
        String sql = SELECT_CURRENT + "WHERE f.id = ? AND f.is_deleted = 0";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    /** The wrapped key of the current version ('' once destroyed). */
    public String getEncryptionKey(int fileId) throws SQLException {
        String sql = "SELECT v.wrapped_key FROM files f JOIN file_versions v "
                + "ON v.file_id = f.id AND v.version_no = f.current_version WHERE f.id = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    public List<FileEntry> listFiles() throws SQLException {
        List<FileEntry> list = new ArrayList<>();
        String sql = SELECT_CURRENT + "WHERE f.is_deleted = 0 ORDER BY f.id DESC";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    /**
     * "Crypto-shredding": the file is hidden AND the wrapped key of every version is erased,
     * so even a leftover copy of an encrypted file can never be decrypted again.
     */
    public void softDelete(int fileId) throws SQLException {
        try (Connection conn = DatabaseManager.connect()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE files SET is_deleted = 1, deleted_at = datetime('now') WHERE id = ?")) {
                    ps.setInt(1, fileId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE file_versions SET wrapped_key = '' WHERE file_id = ?")) {
                    ps.setInt(1, fileId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private FileEntry map(ResultSet rs) throws SQLException {
        int folder = rs.getInt("folder_id");
        Integer folderId = rs.wasNull() ? null : folder;
        return new FileEntry(rs.getInt("id"), rs.getString("filename"), rs.getInt("owner_id"),
                Role.valueOf(rs.getString("access_level")), rs.getString("stored_path"),
                rs.getString("uploaded_at"), folderId, rs.getInt("current_version"));
    }

    // ---------- versions ----------

    /** The encrypted copy of one version and its wrapped key ('' once destroyed). */
    public record StoredVersion(String storedPath, String wrappedKey) {}

    /**
     * Adds a new version in one transaction: the next version number is taken and the file's
     * "current version" moves to it. Returns the new version number.
     */
    public int addVersion(int fileId, int uploadedBy, String wrappedKey, String storedPath,
                          long sizeBytes, String sha256, String comment) throws SQLException {
        try (Connection conn = DatabaseManager.connect()) {
            conn.setAutoCommit(false);
            try {
                int next;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT COALESCE(MAX(version_no), 0) + 1 FROM file_versions WHERE file_id = ?")) {
                    ps.setInt(1, fileId);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); next = rs.getInt(1); }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO file_versions(file_id, version_no, stored_path, wrapped_key, "
                        + "size_bytes, sha256, uploaded_by, comment) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, fileId);
                    ps.setInt(2, next);
                    ps.setString(3, storedPath);
                    ps.setString(4, wrappedKey);
                    ps.setLong(5, sizeBytes);
                    ps.setString(6, sha256);
                    ps.setInt(7, uploadedBy);
                    ps.setString(8, comment);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE files SET current_version = ? WHERE id = ?")) {
                    ps.setInt(1, next);
                    ps.setInt(2, fileId);
                    ps.executeUpdate();
                }
                conn.commit();
                return next;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Every version of a file, newest first, with the uploader's name. */
    public List<VersionEntry> listVersions(int fileId) throws SQLException {
        List<VersionEntry> list = new ArrayList<>();
        String sql = "SELECT v.version_no, v.size_bytes, v.sha256, u.username, v.uploaded_at, v.comment, "
                + "v.wrapped_key, f.current_version FROM file_versions v "
                + "JOIN files f ON f.id = v.file_id JOIN users u ON u.id = v.uploaded_by "
                + "WHERE v.file_id = ? ORDER BY v.version_no DESC";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("wrapped_key");
                    list.add(new VersionEntry(rs.getInt("version_no"), rs.getLong("size_bytes"),
                            rs.getString("sha256"), rs.getString("username"), rs.getString("uploaded_at"),
                            rs.getString("comment"), rs.getInt("version_no") == rs.getInt("current_version"),
                            key != null && !key.isEmpty()));
                }
            }
        }
        return list;
    }

    /** One version's path and key, or null if there is no such version. */
    public StoredVersion getVersion(int fileId, int versionNo) throws SQLException {
        String sql = "SELECT stored_path, wrapped_key FROM file_versions WHERE file_id = ? AND version_no = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fileId);
            ps.setInt(2, versionNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new StoredVersion(rs.getString(1), rs.getString(2));
            }
        }
        return null;
    }

    /** Makes an older version the current one again. */
    public void setCurrentVersion(int fileId, int versionNo) throws SQLException {
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement("UPDATE files SET current_version = ? WHERE id = ?")) {
            ps.setInt(1, versionNo);
            ps.setInt(2, fileId);
            ps.executeUpdate();
        }
    }

    /** The encrypted copies of every version of a file (used when the file is deleted). */
    public List<String> listStoredPaths(int fileId) throws SQLException {
        List<String> paths = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT stored_path FROM file_versions WHERE file_id = ?")) {
            ps.setInt(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) paths.add(rs.getString(1));
            }
        }
        return paths;
    }

    // ---------- folders ----------

    /** Puts a file in a folder, or takes it out when folderId is null. */
    public void setFolder(int fileId, Integer folderId) throws SQLException {
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement("UPDATE files SET folder_id = ? WHERE id = ?")) {
            if (folderId == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setInt(1, folderId);
            ps.setInt(2, fileId);
            ps.executeUpdate();
        }
    }
}
