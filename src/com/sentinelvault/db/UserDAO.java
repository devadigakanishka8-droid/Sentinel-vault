package com.sentinelvault.db;

import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;
import com.sentinelvault.security.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** All database operations for users. PreparedStatement (?) is used everywhere to prevent SQL injection. */
public class UserDAO {

    public void addUser(String username, String plainPassword, Role role) throws SQLException {
        String sql = "INSERT INTO users(username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hashPassword(plainPassword));
            ps.setString(3, role.name());
            ps.executeUpdate();
        }
    }

    public User getUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(rs.getInt("id"), rs.getString("username"),
                            rs.getString("password_hash"), Role.valueOf(rs.getString("role")));
                }
            }
        }
        return null;
    }

    public boolean userExists(String username) throws SQLException {
        return getUserByUsername(username) != null;
    }

    /** Returns the User if the credentials are correct, otherwise null. */
    public User login(String username, String plainPassword) throws SQLException {
        User user = getUserByUsername(username);
        if (user != null && PasswordUtil.checkPassword(plainPassword, user.getPasswordHash())) {
            return user;
        }
        return null;
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users ORDER BY id";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("username"),
                        rs.getString("password_hash"), Role.valueOf(rs.getString("role"))));
            }
        }
        return users;
    }

    /** id -> username only (no password hashes), used to show who owns a file. */
    public Map<Integer, String> getUsernames() throws SQLException {
        Map<Integer, String> names = new HashMap<>();
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT id, username FROM users");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) names.put(rs.getInt(1), rs.getString(2));
        }
        return names;
    }

    // ---------- Account security ----------

    /** When the account unlocks, or null if it is not locked. */
    public LocalDateTime getLockedUntil(int userId) throws SQLException {
        String sql = "SELECT locked_until FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getString(1) != null) return LocalDateTime.parse(rs.getString(1));
            }
        }
        return null;
    }

    /** Counts one more wrong password. Returns true if this attempt just locked the account. */
    public boolean recordFailedAttempt(int userId, int maxAttempts, int lockMinutes) throws SQLException {
        try (Connection conn = DatabaseManager.connect()) {
            int attempts = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(failed_attempts, 0) FROM users WHERE id = ?")) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) attempts = rs.getInt(1);
                }
            }
            attempts++;
            if (attempts >= maxAttempts) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE users SET failed_attempts = 0, locked_until = ? WHERE id = ?")) {
                    ps.setString(1, LocalDateTime.now().plusMinutes(lockMinutes).toString());
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                }
                return true;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE users SET failed_attempts = ? WHERE id = ?")) {
                ps.setInt(1, attempts);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }
            return false;
        }
    }

    public void resetFailedAttempts(int userId) throws SQLException {
        String sql = "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE id = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    public boolean isActive(int userId) throws SQLException {
        String sql = "SELECT COALESCE(is_active, 1) FROM users WHERE id = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) == 1;
            }
        }
        return false;
    }

    public void setActive(int userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, active ? 1 : 0);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void updatePassword(int userId, String plainNewPassword) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.hashPassword(plainNewPassword));
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void updateRole(int userId, Role newRole) throws SQLException {
        String sql = "UPDATE users SET role = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newRole.name());
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }
}
