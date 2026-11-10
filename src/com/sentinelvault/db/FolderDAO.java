package com.sentinelvault.db;

import com.sentinelvault.model.FolderEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Folders group files. Deleting a folder is "soft", like files, and only an empty folder can be deleted. */
public class FolderDAO {

    public int createFolder(String name, int ownerId) throws SQLException {
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO folders(name, owner_id) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, ownerId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Could not get the new folder id");
                return keys.getInt(1);
            }
        }
    }

    public List<FolderEntry> listFolders() throws SQLException {
        List<FolderEntry> list = new ArrayList<>();
        String sql = "SELECT id, name, owner_id, created_at FROM folders WHERE is_deleted = 0 ORDER BY name COLLATE NOCASE";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public FolderEntry getFolder(int id) throws SQLException {
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, owner_id, created_at FROM folders WHERE id = ? AND is_deleted = 0")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public boolean nameExists(String name) throws SQLException {
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM folders WHERE is_deleted = 0 AND parent_id IS NULL AND name = ? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** True when no active file is inside the folder. */
    public boolean isEmpty(int folderId) throws SQLException {
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM files WHERE folder_id = ? AND is_deleted = 0 LIMIT 1")) {
            ps.setInt(1, folderId);
            try (ResultSet rs = ps.executeQuery()) { return !rs.next(); }
        }
    }

    public void softDelete(int folderId) throws SQLException {
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement("UPDATE folders SET is_deleted = 1 WHERE id = ?")) {
            ps.setInt(1, folderId);
            ps.executeUpdate();
        }
    }

    private FolderEntry map(ResultSet rs) throws SQLException {
        return new FolderEntry(rs.getInt("id"), rs.getString("name"), rs.getInt("owner_id"), rs.getString("created_at"));
    }
}
