package com.sentinelvault.ui;

import com.sentinelvault.model.AuditLogEntry;
import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.FolderEntry;
import com.sentinelvault.model.VersionEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;

import java.io.File;
import java.util.List;

/**
 * Everything the screens need from the backend, in one place.
 * BackendApi is the real implementation; the UI never touches the database directly.
 */
public interface Api {

    record UserRow(int id, String username, Role role, boolean active) implements java.io.Serializable {}

    /** Returns the user, or null for a wrong username/password. Throws if locked or disabled. */
    User login(String username, String password) throws Exception;
    User register(String username, String password) throws Exception;

    /** True when no accounts exist yet (first run). */
    boolean needsSetup() throws Exception;
    /** First run only: creates the Owner account. */
    User registerFirstOwner(String username, String password) throws Exception;

    /** Re-reads the user from the database (role may have changed). Null if disabled or deleted. */
    User refresh(User user) throws Exception;

    List<FileEntry> files(User user) throws Exception;
    String ownerName(int userId);

    /** Id to username for every account (used by the file tables). Empty if not available. */
    default java.util.Map<Integer, String> ownerNames() { return java.util.Map.of(); }
    int upload(User user, File source, Role accessLevel) throws Exception;
    void download(User user, int fileId, File destination) throws Exception;
    void delete(User user, int fileId) throws Exception;

    // ----- folders -----
    List<FolderEntry> folders(User user) throws Exception;
    int createFolder(User user, String name) throws Exception;
    /** folderId null takes the file out of any folder. */
    void moveFile(User user, int fileId, Integer folderId) throws Exception;
    void deleteFolder(User user, int folderId) throws Exception;

    // ----- versions -----
    List<VersionEntry> versions(User user, int fileId) throws Exception;
    /** Returns the new version number. */
    int uploadVersion(User user, int fileId, File source, String comment) throws Exception;
    void downloadVersion(User user, int fileId, int versionNo, File destination) throws Exception;
    void restoreVersion(User user, int fileId, int versionNo) throws Exception;

    /** OWNER sees everything; everyone else sees only their own activity. */
    List<AuditLogEntry> audit(User user) throws Exception;
    boolean verifyAudit(User user) throws Exception;

    List<UserRow> users(User user) throws Exception;
    void changeRole(User actor, int targetId, Role role) throws Exception;
    void setActive(User actor, int targetId, boolean active) throws Exception;
    void changePassword(User user, String current, String next, String confirm) throws Exception;
}
