package com.sentinelvault.service;

import com.sentinelvault.db.AuditLogDAO;
import com.sentinelvault.db.UserDAO;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;
import com.sentinelvault.security.PasswordUtil;
import com.sentinelvault.security.PermissionService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * User management: sign-up, login, password change, role changes and enabling/disabling accounts.
 * Every action checks permission first and writes the audit log.
 */
public class UserService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 5;

    // Checked when the username doesn't exist, so a missing user takes as long as a wrong password.
    // (Otherwise an attacker could discover which usernames exist by timing the responses.)
    private static final String DUMMY_HASH = PasswordUtil.hashPassword("not-a-real-password-1");

    private final UserDAO userDAO = new UserDAO();
    private final AuditLogDAO auditDAO = new AuditLogDAO();

    /** Anyone can sign up, but always as a VIEWER. Only an OWNER can promote them later. */
    public User registerUser(String username, String plainPassword) throws Exception {
        return create(username, plainPassword, Role.VIEWER);
    }

    /** True while the database has no accounts at all (the very first run). */
    public boolean isFirstRun() throws Exception {
        return userDAO.getAllUsers().isEmpty();
    }

    /**
     * First run only: the first account becomes the OWNER, otherwise nobody could ever promote anyone.
     * Refused as soon as any account exists, so it cannot be used to grab ownership later.
     */
    public synchronized User registerFirstOwner(String username, String plainPassword) throws Exception {
        if (!isFirstRun()) throw new SecurityException("This vault already has an owner.");
        return create(username, plainPassword, Role.OWNER);
    }

    private User create(String username, String plainPassword, Role role) throws Exception {
        if (username == null || !username.trim().matches("[A-Za-z0-9_.-]{3,30}")) {
            throw new IllegalArgumentException("Username must be 3-30 characters: letters, digits, _ . -");
        }
        String policyError = PasswordUtil.validatePolicy(plainPassword);
        if (policyError != null) throw new IllegalArgumentException(policyError);

        username = username.trim();
        if (userDAO.userExists(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }
        userDAO.addUser(username, plainPassword, role);
        User created = userDAO.getUserByUsername(username);
        auditDAO.logAction(created.getId(), null, "REGISTER", "SUCCESS");
        return created;
    }

    /**
     * Returns the User on success, or null for a wrong username/password.
     * Throws SecurityException if the account is disabled or temporarily locked.
     */
    public User login(String username, String plainPassword) throws Exception {
        String password = plainPassword == null ? "" : plainPassword;
        User existing = username == null ? null : userDAO.getUserByUsername(username.trim());

        if (existing == null) {
            PasswordUtil.checkPassword(password, DUMMY_HASH);   // keep the timing the same
            return null;
        }
        int id = existing.getId();

        if (!userDAO.isActive(id)) {
            auditDAO.logAction(id, null, "LOGIN", "DENIED");
            throw new SecurityException("This account has been disabled.");
        }
        LocalDateTime lockedUntil = userDAO.getLockedUntil(id);
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            auditDAO.logAction(id, null, "LOGIN_LOCKED", "DENIED");
            throw new SecurityException("Too many failed attempts. Try again after "
                    + lockedUntil.format(DateTimeFormatter.ofPattern("HH:mm")) + ".");
        }

        if (PasswordUtil.checkPassword(password, existing.getPasswordHash())) {
            userDAO.resetFailedAttempts(id);
            auditDAO.logAction(id, null, "LOGIN", "SUCCESS");
            return existing;
        }

        boolean nowLocked = userDAO.recordFailedAttempt(id, MAX_FAILED_ATTEMPTS, LOCK_MINUTES);
        auditDAO.logAction(id, null, "LOGIN", "DENIED");
        if (nowLocked) auditDAO.logAction(id, null, "ACCOUNT_LOCKED", "DENIED");
        return null;
    }

    /** Verifies the current password, enforces the policy, then stores the new hash. */
    public void changePassword(User user, String currentPassword, String newPassword, String confirmPassword)
            throws Exception {
        User fresh = userDAO.getUserByUsername(user.getUsername());
        if (fresh == null || !userDAO.isActive(fresh.getId())) {
            throw new SecurityException("Account not available.");
        }
        if (currentPassword == null || !PasswordUtil.checkPassword(currentPassword, fresh.getPasswordHash())) {
            auditDAO.logAction(fresh.getId(), null, "CHANGE_PASSWORD", "DENIED");
            throw new SecurityException("Current password is incorrect.");
        }
        if (newPassword == null || !newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("The new passwords do not match.");
        }
        String policyError = PasswordUtil.validatePolicy(newPassword);
        if (policyError != null) throw new IllegalArgumentException(policyError);
        if (newPassword.equals(currentPassword)) {
            throw new IllegalArgumentException("The new password must be different from the current one.");
        }
        userDAO.updatePassword(fresh.getId(), newPassword);
        auditDAO.logAction(fresh.getId(), null, "CHANGE_PASSWORD", "SUCCESS");
    }

    /** Only an OWNER may change roles. The acting user's role is re-read from the database, not trusted. */
    public void changeRole(User actingUser, int targetUserId, Role newRole) throws Exception {
        User acting = requireAllowed(actingUser, "CHANGE_ROLE");
        if (acting.getId() == targetUserId) {
            throw new IllegalArgumentException("You cannot change your own role.");   // avoids locking out the last owner
        }
        userDAO.updateRole(targetUserId, newRole);
        auditDAO.logAction(acting.getId(), null, "CHANGE_ROLE", "SUCCESS");
    }

    /** Only an OWNER may list every account. */
    public List<User> listUsers(User actingUser) throws Exception {
        requireAllowed(actingUser, "LIST_USERS");
        return userDAO.getAllUsers();
    }

    /** Only an OWNER may disable or re-enable accounts. */
    public void setUserActive(User actingUser, int targetUserId, boolean active) throws Exception {
        String action = active ? "ENABLE_USER" : "DISABLE_USER";
        User acting = requireAllowed(actingUser, action);
        if (acting.getId() == targetUserId) {
            throw new IllegalArgumentException("You cannot disable your own account.");
        }
        userDAO.setActive(targetUserId, active);
        auditDAO.logAction(acting.getId(), null, action, "SUCCESS");
    }

    /** Loads the real, current user from the database and checks the MANAGE_ROLES permission. */
    private User requireAllowed(User actingUser, String action) throws Exception {
        User acting = userDAO.getUserByUsername(actingUser.getUsername());
        if (acting == null || !userDAO.isActive(acting.getId())
                || !PermissionService.canPerform(acting.getRole(), PermissionService.MANAGE_ROLES)) {
            auditDAO.logAction(actingUser.getId(), null, action, "DENIED");
            throw new SecurityException("Only an OWNER can do this.");
        }
        return acting;
    }
}
