package com.sentinelvault.service;

import com.sentinelvault.db.AuditLogDAO;
import com.sentinelvault.db.FileDAO;
import com.sentinelvault.db.FolderDAO;
import com.sentinelvault.db.UserDAO;
import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.FolderEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;
import com.sentinelvault.security.PermissionService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Folders group files. A folder never gives access to anything: files inside it keep their own
 * access level, so moving a file into a folder cannot expose it.
 */
public class FolderService {

    private final FolderDAO folderDAO = new FolderDAO();
    private final FileDAO fileDAO = new FileDAO();
    private final UserDAO userDAO = new UserDAO();
    private final AuditLogDAO auditDAO = new AuditLogDAO();

    /** The role held by the screen is never trusted: re-read the user from the database. */
    private User currentUser(User caller) throws Exception {
        User fresh = caller == null ? null : userDAO.getUserByUsername(caller.getUsername());
        if (fresh == null || !userDAO.isActive(fresh.getId())) throw new SecurityException("Account not available.");
        return fresh;
    }

    /** Editors and Owners may create folders. Returns the new folder id. */
    public int createFolder(User caller, String name) throws Exception {
        User user = currentUser(caller);
        if (!PermissionService.canPerform(user.getRole(), PermissionService.UPLOAD)) {
            auditDAO.logAction(user.getId(), null, "CREATE_FOLDER", "DENIED");
            throw new SecurityException("Your role (" + user.getRole() + ") cannot create folders.");
        }
        String clean = name == null ? "" : name.trim();
        if (!clean.matches("[\\p{L}\\p{N} _.()\\-]{1,60}")) {
            throw new IllegalArgumentException("Folder name: 1 to 60 letters, digits, spaces or _ . - ( )");
        }
        if (folderDAO.nameExists(clean)) throw new IllegalArgumentException("A folder named '" + clean + "' already exists.");
        int id = folderDAO.createFolder(clean, user.getId());
        auditDAO.logAction(user.getId(), null, "CREATE_FOLDER", "SUCCESS");
        return id;
    }

    /**
     * Owners see every folder. Others see the folders they created and the folders that hold at
     * least one file they may open, so a folder name does not reveal files they cannot see.
     */
    public List<FolderEntry> listFolders(User caller) throws Exception {
        User user = currentUser(caller);
        List<FolderEntry> all = folderDAO.listFolders();
        if (user.getRole() == Role.OWNER) return all;
        Set<Integer> usable = new HashSet<>();
        for (FileEntry f : fileDAO.listFiles()) {
            boolean uploader = f.getOwnerId() == user.getId();
            if (f.getFolderId() != null && PermissionService.canAccessFile(user.getRole(), f.getAccessLevel(), uploader)) {
                usable.add(f.getFolderId());
            }
        }
        List<FolderEntry> visible = new ArrayList<>();
        for (FolderEntry folder : all) {
            if (folder.getOwnerId() == user.getId() || usable.contains(folder.getId())) visible.add(folder);
        }
        return visible;
    }

    /** Moves a file into a folder (or out of any folder when folderId is null). Uploader or Owner only. */
    public void moveFile(User caller, int fileId, Integer folderId) throws Exception {
        User user = currentUser(caller);
        FileEntry file = fileDAO.getFile(fileId);
        if (file == null) throw new IllegalArgumentException("File " + fileId + " not found.");
        boolean uploader = file.getOwnerId() == user.getId();
        boolean allowed = PermissionService.canPerform(user.getRole(), PermissionService.UPLOAD)
                && PermissionService.canAccessFile(user.getRole(), file.getAccessLevel(), uploader)
                && (uploader || user.getRole() == Role.OWNER);
        if (!allowed) {
            auditDAO.logAction(user.getId(), fileId, "MOVE_FILE", "DENIED");
            throw new SecurityException("You cannot move this file.");
        }
        if (folderId != null && folderDAO.getFolder(folderId) == null) {
            throw new IllegalArgumentException("That folder no longer exists.");
        }
        fileDAO.setFolder(fileId, folderId);
        auditDAO.logAction(user.getId(), fileId, "MOVE_FILE", "SUCCESS");
    }

    /** Owner only, and only when the folder is empty. */
    public void deleteFolder(User caller, int folderId) throws Exception {
        User user = currentUser(caller);
        if (!PermissionService.canPerform(user.getRole(), PermissionService.DELETE)) {
            auditDAO.logAction(user.getId(), null, "DELETE_FOLDER", "DENIED");
            throw new SecurityException("Only an Owner can delete folders.");
        }
        if (folderDAO.getFolder(folderId) == null) throw new IllegalArgumentException("That folder no longer exists.");
        if (!folderDAO.isEmpty(folderId)) throw new IllegalArgumentException("The folder is not empty. Move or delete its files first.");
        folderDAO.softDelete(folderId);
        auditDAO.logAction(user.getId(), null, "DELETE_FOLDER", "SUCCESS");
    }
}
