package com.sentinelvault.service;

import com.sentinelvault.db.AuditLogDAO;
import com.sentinelvault.db.FileDAO;
import com.sentinelvault.db.UserDAO;
import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;
import com.sentinelvault.security.EncryptionUtil;
import com.sentinelvault.security.KeyVault;
import com.sentinelvault.security.PermissionService;

import javax.crypto.AEADBadTagException;
import javax.crypto.SecretKey;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ties everything together. Every action follows the same steps:
 *   1. load the REAL user from the database   2. check permission (RBAC + file-level access)
 *   3. do the work (encrypt / decrypt / DB)   4. write the audit log
 * The UI only needs to call this class. KeyVault.unlock(...) must be called once at startup.
 */
public class FileService {

    private static final File VAULT_DIR = new File("vault_storage");
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;   // files are processed in memory

    private final FileDAO fileDAO = new FileDAO();
    private final UserDAO userDAO = new UserDAO();
    private final AuditLogDAO auditDAO = new AuditLogDAO();

    public FileService() {
        if (!VAULT_DIR.exists()) VAULT_DIR.mkdirs();
    }

    /** Encrypts the chosen file, stores it in the vault and records it. Returns the new file id. */
    public int uploadFile(User caller, File source, Role accessLevel) throws Exception {
        User user = currentUser(caller);
        if (!PermissionService.canPerform(user.getRole(), PermissionService.UPLOAD)) {
            auditDAO.logAction(user.getId(), null, "UPLOAD", "DENIED");
            throw new SecurityException("Your role (" + user.getRole() + ") cannot upload files.");
        }
        if (source == null || !source.isFile()) throw new IllegalArgumentException("Choose a valid file.");
        if (source.length() > MAX_FILE_BYTES) throw new IllegalArgumentException("File is too large (max 50 MB).");

        SecretKey key = EncryptionUtil.generateKey();
        String wrappedKey = KeyVault.wrap(key);   // fails early if the vault is locked
        // Random stored name so the real filename isn't exposed on disk
        File stored = new File(VAULT_DIR, UUID.randomUUID() + ".enc");
        EncryptionUtil.encryptFile(source, stored, key);

        try {
            int fileId = fileDAO.addFile(source.getName(), user.getId(), accessLevel, wrappedKey,
                    stored.getPath(), source.length(), sha256Of(source));
            auditDAO.logAction(user.getId(), fileId, "UPLOAD", "SUCCESS");
            return fileId;
        } catch (Exception e) {
            stored.delete();   // don't leave an orphaned encrypted file behind
            throw e;
        }
    }

    /** Decrypts a vault file into the destination file. */
    public void downloadFile(User caller, int fileId, File destination) throws Exception {
        User user = currentUser(caller);
        FileEntry entry = requireFile(fileId);
        boolean isUploader = entry.getOwnerId() == user.getId();
        if (!PermissionService.canPerform(user.getRole(), PermissionService.DOWNLOAD)
                || !PermissionService.canAccessFile(user.getRole(), entry.getAccessLevel(), isUploader)) {
            auditDAO.logAction(user.getId(), fileId, "DOWNLOAD", "DENIED");
            throw new SecurityException("You do not have access to this file.");
        }
        try {
            SecretKey key = KeyVault.unwrap(fileDAO.getEncryptionKey(fileId));
            EncryptionUtil.decryptFile(new File(entry.getStoredPath()), destination, key);
        } catch (AEADBadTagException e) {
            auditDAO.logAction(user.getId(), fileId, "DOWNLOAD", "FAILED");
            throw new SecurityException("Integrity check failed: the file was modified or the key is wrong.");
        }
        auditDAO.logAction(user.getId(), fileId, "DOWNLOAD", "SUCCESS");
    }

    /** OWNER only. Hides the file, destroys its key and removes the encrypted copy from disk. */
    public void deleteFile(User caller, int fileId) throws Exception {
        User user = currentUser(caller);
        FileEntry entry = requireFile(fileId);
        if (!PermissionService.canPerform(user.getRole(), PermissionService.DELETE)) {
            auditDAO.logAction(user.getId(), fileId, "DELETE", "DENIED");
            throw new SecurityException("Your role (" + user.getRole() + ") cannot delete files.");
        }
        fileDAO.softDelete(fileId);                 // key erased: the data is now unrecoverable
        new File(entry.getStoredPath()).delete();   // and the encrypted copy is removed too
        auditDAO.logAction(user.getId(), fileId, "DELETE", "SUCCESS");
    }

    /** Only the files this user is allowed to open. */
    public List<FileEntry> listFiles(User caller) throws Exception {
        User user = currentUser(caller);
        if (!PermissionService.canPerform(user.getRole(), PermissionService.VIEW)) {
            auditDAO.logAction(user.getId(), null, "VIEW", "DENIED");
            throw new SecurityException("Your role cannot view files.");
        }
        List<FileEntry> visible = new ArrayList<>();
        for (FileEntry entry : fileDAO.listFiles()) {
            boolean isUploader = entry.getOwnerId() == user.getId();
            if (PermissionService.canAccessFile(user.getRole(), entry.getAccessLevel(), isUploader)) {
                visible.add(entry);
            }
        }
        return visible;
    }

    /** SHA-256 of the original file, stored so the content can be proven unchanged. */
    private static String sha256Of(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    /** Never trust the role held by the UI: a demoted or disabled user must lose access immediately. */
    private User currentUser(User caller) throws Exception {
        User fresh = caller == null ? null : userDAO.getUserByUsername(caller.getUsername());
        if (fresh == null || !userDAO.isActive(fresh.getId())) {
            throw new SecurityException("Account not available.");
        }
        return fresh;
    }

    private FileEntry requireFile(int fileId) throws Exception {
        FileEntry entry = fileDAO.getFile(fileId);
        if (entry == null) throw new IllegalArgumentException("File " + fileId + " not found.");
        return entry;
    }
}
