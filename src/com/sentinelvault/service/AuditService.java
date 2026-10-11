package com.sentinelvault.service;

import com.sentinelvault.db.AuditLogDAO;
import com.sentinelvault.db.UserDAO;
import com.sentinelvault.model.AuditLogEntry;
import com.sentinelvault.model.User;
import com.sentinelvault.security.PermissionService;

import java.util.ArrayList;
import java.util.List;

/**
 * Who may read the audit trail. The full log is OWNER-only (checked against the real role in the database);
 * everyone else can only see their own entries.
 */
public class AuditService {

    private final AuditLogDAO auditDAO = new AuditLogDAO();
    private final UserDAO userDAO = new UserDAO();

    /** Every entry, newest first. OWNER only. */
    public List<AuditLogEntry> getAllLogs(User caller) throws Exception {
        requireOwner(caller);
        return auditDAO.getAllLogs();
    }

    /** Only the caller's own entries, newest first. */
    public List<AuditLogEntry> getMyLogs(User caller) throws Exception {
        User user = current(caller);
        List<AuditLogEntry> mine = new ArrayList<>();
        for (AuditLogEntry e : auditDAO.getAllLogs()) {
            if (e.getUsername().equals(user.getUsername())) mine.add(e);
        }
        return mine;
    }

    /** Re-checks the whole hash chain. OWNER only. */
    public boolean verifyIntegrity(User caller) throws Exception {
        requireOwner(caller);
        return auditDAO.verifyIntegrity();
    }

    private User requireOwner(User caller) throws Exception {
        User user = current(caller);
        if (!PermissionService.canPerform(user.getRole(), PermissionService.MANAGE_ROLES)) {
            auditDAO.logAction(user.getId(), null, "VIEW_AUDIT", "DENIED");
            throw new SecurityException("Only an OWNER can read the full audit log.");
        }
        return user;
    }

    private User current(User caller) throws Exception {
        User fresh = caller == null ? null : userDAO.getUserByUsername(caller.getUsername());
        if (fresh == null || !userDAO.isActive(fresh.getId())) {
            throw new SecurityException("Account not available.");
        }
        return fresh;
    }
}
