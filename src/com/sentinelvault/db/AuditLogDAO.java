package com.sentinelvault.db;

import com.sentinelvault.model.AuditLogEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes and reads the audit trail: who did what, to which file, when, and whether it was allowed.
 *
 * Tamper-evident: every row stores a SHA-256 hash of its own contents PLUS the previous row's hash
 * (a hash chain, like a tiny blockchain). Editing or deleting any old row breaks the chain, and
 * verifyIntegrity() detects it.
 */
public class AuditLogDAO {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Object LOCK = new Object();   // one writer at a time keeps the chain in order

    /** fileId may be null for actions that aren't about a file (e.g. LOGIN). status: SUCCESS, DENIED or FAILED. */
    public void logAction(int userId, Integer fileId, String action, String status) throws SQLException {
        synchronized (LOCK) {
            String timestamp = LocalDateTime.now().format(FORMAT);
            String sql = "INSERT INTO audit_logs(user_id, file_id, action, status, timestamp, row_hash) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.connect()) {
                String previousHash = lastHash(conn);
                String hash = computeHash(previousHash, userId, fileId, action, status, timestamp);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    if (fileId != null) ps.setInt(2, fileId); else ps.setNull(2, Types.INTEGER);
                    ps.setString(3, action);
                    ps.setString(4, status);
                    ps.setString(5, timestamp);
                    ps.setString(6, hash);
                    ps.executeUpdate();
                }
            }
        }
    }

    /** Re-computes the whole chain. Returns false if any row was changed, removed from the middle, or reordered. */
    public boolean verifyIntegrity() throws SQLException {
        String sql = "SELECT user_id, file_id, action, status, timestamp, row_hash FROM audit_logs ORDER BY id";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            String previous = "";
            boolean chainStarted = false;
            while (rs.next()) {
                String stored = rs.getString("row_hash");
                if (stored == null) {
                    if (chainStarted) return false;   // a hashed row was blanked out
                    continue;                         // older rows written before hashing existed
                }
                chainStarted = true;
                int fileIdValue = rs.getInt("file_id");
                Integer fileId = rs.wasNull() ? null : fileIdValue;
                String expected = computeHash(previous, rs.getInt("user_id"), fileId,
                        rs.getString("action"), rs.getString("status"), rs.getString("timestamp"));
                if (!expected.equals(stored)) return false;
                previous = stored;
            }
        }
        return true;
    }

    /** Newest first, with usernames and filenames filled in. */
    public List<AuditLogEntry> getAllLogs() throws SQLException {
        List<AuditLogEntry> list = new ArrayList<>();
        String sql = "SELECT a.timestamp, u.username, a.action, a.status, f.filename "
                + "FROM audit_logs a "
                + "JOIN users u ON a.user_id = u.id "
                + "LEFT JOIN files f ON a.file_id = f.id "
                + "ORDER BY a.id DESC";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String filename = rs.getString("filename");
                list.add(new AuditLogEntry(rs.getString("timestamp"), rs.getString("username"),
                        rs.getString("action"), filename == null ? "-" : filename,
                        rs.getString("status")));
            }
        }
        return list;
    }

    private String lastHash(Connection conn) throws SQLException {
        String sql = "SELECT row_hash FROM audit_logs ORDER BY id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getString(1) != null) return rs.getString(1);
        }
        return "";
    }

    private static String computeHash(String previousHash, int userId, Integer fileId,
                                      String action, String status, String timestamp) {
        String data = previousHash + "|" + userId + "|" + (fileId == null ? "-" : fileId)
                + "|" + action + "|" + status + "|" + timestamp;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
