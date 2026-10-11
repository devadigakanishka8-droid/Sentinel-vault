package com.sentinelvault.db;

import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.Role;

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
            "SELECT f.id, f.filename, f.owner_id, f.access_level, f.uploaded_at, v.stored_path "
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
        return new FileEntry(rs.getInt("id"), rs.getString("filename"), rs.getInt("owner_id"),
                Role.valueOf(rs.getString("access_level")), rs.getString("stored_path"),
                rs.getString("uploaded_at"));
    }
}
