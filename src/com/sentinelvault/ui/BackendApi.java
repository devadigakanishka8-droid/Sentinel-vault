package com.sentinelvault.ui;

import com.sentinelvault.db.AuditLogDAO;
import com.sentinelvault.db.UserDAO;
import com.sentinelvault.model.AuditLogEntry;
import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;
import com.sentinelvault.service.FileService;
import com.sentinelvault.service.UserService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Connects the screens to the real backend services. All permission checks stay in the backend. */
public class BackendApi implements Api {

    private final UserService userService = new UserService();
    private final FileService fileService = new FileService();
    private final UserDAO userDAO = new UserDAO();
    private final AuditLogDAO auditDAO = new AuditLogDAO();
    private final Map<Integer, String> names = new ConcurrentHashMap<>();

    @Override public User login(String username, String password) throws Exception {
        return userService.login(username, password);
    }

    @Override public User register(String username, String password) throws Exception {
        return userService.registerUser(username, password);
    }

    @Override public boolean needsSetup() throws Exception {
        return userService.isFirstRun();
    }

    @Override public User registerFirstOwner(String username, String password) throws Exception {
        return userService.registerFirstOwner(username, password);
    }

    @Override public User refresh(User user) throws Exception {
        User fresh = userDAO.getUserByUsername(user.getUsername());
        if (fresh == null || !userDAO.isActive(fresh.getId())) return null;
        return fresh;
    }

    @Override public List<FileEntry> files(User user) throws Exception {
        List<FileEntry> list = fileService.listFiles(user);
        loadNames();
        return list;
    }

    @Override public String ownerName(int userId) {
        return names.getOrDefault(userId, "user " + userId);
    }

    @Override public int upload(User user, File source, Role accessLevel) throws Exception {
        return fileService.uploadFile(user, source, accessLevel);
    }

    @Override public void download(User user, int fileId, File destination) throws Exception {
        fileService.downloadFile(user, fileId, destination);
    }

    @Override public void delete(User user, int fileId) throws Exception {
        fileService.deleteFile(user, fileId);
    }

    @Override public List<AuditLogEntry> audit(User user) throws Exception {
        User fresh = require(user);
        List<AuditLogEntry> all = auditDAO.getAllLogs();
        if (fresh.getRole() == Role.OWNER) return all;
        List<AuditLogEntry> mine = new ArrayList<>();
        for (AuditLogEntry e : all) {
            if (e.getUsername().equalsIgnoreCase(fresh.getUsername())) mine.add(e);
        }
        return mine;
    }

    @Override public boolean verifyAudit(User user) throws Exception {
        requireOwner(user);
        return auditDAO.verifyIntegrity();
    }

    @Override public List<UserRow> users(User user) throws Exception {
        requireOwner(user);
        List<UserRow> rows = new ArrayList<>();
        for (User u : userDAO.getAllUsers()) {
            rows.add(new UserRow(u.getId(), u.getUsername(), u.getRole(), userDAO.isActive(u.getId())));
        }
        return rows;
    }

    @Override public void changeRole(User actor, int targetId, Role role) throws Exception {
        userService.changeRole(actor, targetId, role);
    }

    @Override public void setActive(User actor, int targetId, boolean active) throws Exception {
        userService.setUserActive(actor, targetId, active);
    }

    @Override public void changePassword(User user, String current, String next, String confirm) throws Exception {
        userService.changePassword(user, current, next, confirm);
    }

    // ---------- helpers ----------
    private User require(User user) throws Exception {
        User fresh = refresh(user);
        if (fresh == null) throw new SecurityException("Account not available.");
        return fresh;
    }

    private void requireOwner(User user) throws Exception {
        if (require(user).getRole() != Role.OWNER) throw new SecurityException("Only an OWNER can do this.");
    }

    private void loadNames() throws Exception {
        for (User u : userDAO.getAllUsers()) names.put(u.getId(), u.getUsername());
    }
}
