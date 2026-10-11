package com.sentinelvault;

import com.sentinelvault.db.AuditLogDAO;
import com.sentinelvault.db.DatabaseManager;
import com.sentinelvault.db.FileDAO;
import com.sentinelvault.db.UserDAO;
import com.sentinelvault.model.AuditLogEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;
import com.sentinelvault.security.KeyVault;
import com.sentinelvault.service.FileService;
import com.sentinelvault.service.UserService;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Runs the whole backend end-to-end. Safe to run again and again. */
public class TestBackend {

    private static final String MASTER_PASSPHRASE = "demo-master-passphrase-2026";
    private static int passed = 0, failed = 0;

    private static void check(String name, boolean ok) {
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + name);
        if (ok) passed++; else failed++;
    }

    public static void main(String[] args) throws Exception {
        // ---------- 0. Master key ----------
        System.out.println("0. Master key vault");
        KeyVault.unlock(MASTER_PASSPHRASE.toCharArray());
        check("vault unlocks with the right passphrase", KeyVault.isUnlocked());
        KeyVault.lock();
        boolean wrongRejected = false;
        try { KeyVault.unlock("this-is-the-wrong-passphrase".toCharArray()); }
        catch (SecurityException e) { wrongRejected = true; }
        check("wrong passphrase is rejected", wrongRejected && !KeyVault.isUnlocked());
        KeyVault.unlock(MASTER_PASSPHRASE.toCharArray());

        DatabaseManager.initializeTables();
        UserDAO users = new UserDAO();
        FileDAO fileDAO = new FileDAO();
        UserService userService = new UserService();
        FileService service = new FileService();
        AuditLogDAO audit = new AuditLogDAO();

        // ---------- 1. Users and login ----------
        System.out.println("1. Users and login");
        if (!users.userExists("kanishka")) users.addUser("kanishka", "owner123", Role.OWNER);
        if (!users.userExists("vrinda"))   users.addUser("vrinda", "editor123", Role.EDITOR);
        if (!users.userExists("tanushka")) users.addUser("tanushka", "viewer123", Role.VIEWER);

        User owner  = userService.login("kanishka", "owner123");
        User editor = userService.login("vrinda", "editor123");
        User viewer = userService.login("tanushka", "viewer123");
        check("correct password logs in", owner != null && editor != null && viewer != null);
        check("wrong password is rejected", userService.login("kanishka", "wrong") == null);
        check("unknown user is rejected", userService.login("nobody", "x") == null);
        check("roles loaded correctly", owner.getRole() == Role.OWNER
                && editor.getRole() == Role.EDITOR && viewer.getRole() == Role.VIEWER);
        check("password is stored hashed, not plain", owner.getPasswordHash().startsWith("$2"));

        // ---------- 2. Encrypted upload / download ----------
        System.out.println("2. Encrypted upload / download");
        File original = File.createTempFile("secret", ".txt");
        Files.writeString(original.toPath(), "Top secret SentinelVault data");

        int fileId = service.uploadFile(editor, original, Role.VIEWER);
        check("editor can upload", fileId > 0);

        String storedPath = fileDAO.getFile(fileId).getStoredPath();
        String onDisk = new String(Files.readAllBytes(new File(storedPath).toPath()), "ISO-8859-1");
        check("file on disk is encrypted (plain text not visible)", !onDisk.contains("Top secret"));
        check("key in the database is wrapped, not the raw key", fileDAO.getEncryptionKey(fileId).length() == 80);

        File restored = File.createTempFile("restored", ".txt");
        service.downloadFile(viewer, fileId, restored);
        check("viewer can download and content matches",
                Files.readString(restored.toPath()).equals("Top secret SentinelVault data"));

        try (Connection c = DatabaseManager.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT size_bytes, sha256 FROM file_versions WHERE file_id = ? AND version_no = 1")) {
            ps.setInt(1, fileId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                check("version 1 stored with size and SHA-256",
                        rs.getLong(1) == original.length() && rs.getString(2).length() == 64);
            }
        }

        // ---------- 3. Permissions ----------
        System.out.println("3. Permissions");
        boolean viewerUploadBlocked = false;
        try { service.uploadFile(viewer, original, Role.VIEWER); }
        catch (SecurityException e) { viewerUploadBlocked = true; }
        check("viewer cannot upload", viewerUploadBlocked);

        boolean editorDeleteBlocked = false;
        try { service.deleteFile(editor, fileId); }
        catch (SecurityException e) { editorDeleteBlocked = true; }
        check("editor cannot delete", editorDeleteBlocked);

        int editorOnlyId = service.uploadFile(editor, original, Role.EDITOR);
        File restored2 = File.createTempFile("restored2", ".txt");
        boolean viewerFileBlocked = false;
        try { service.downloadFile(viewer, editorOnlyId, restored2); }
        catch (SecurityException e) { viewerFileBlocked = true; }
        check("viewer cannot open an EDITOR-level file", viewerFileBlocked);
        check("viewer's file list hides it", service.listFiles(viewer).stream().noneMatch(f -> f.getId() == editorOnlyId));
        service.downloadFile(owner, editorOnlyId, restored2);
        check("owner can open it", Files.readString(restored2.toPath()).equals("Top secret SentinelVault data"));

        // ---------- 4. Tamper detection ----------
        System.out.println("4. Tamper detection");
        File storedFile = new File(fileDAO.getFile(editorOnlyId).getStoredPath());
        byte[] bytes = Files.readAllBytes(storedFile.toPath());
        bytes[20] ^= 1;   // flip one bit inside the ciphertext
        Files.write(storedFile.toPath(), bytes);
        boolean tamperCaught = false;
        try { service.downloadFile(owner, editorOnlyId, restored2); }
        catch (SecurityException e) { tamperCaught = true; }
        check("a modified encrypted file is detected", tamperCaught);

        // ---------- 5. Delete destroys the key ----------
        System.out.println("5. Delete");
        service.deleteFile(owner, fileId);
        service.deleteFile(owner, editorOnlyId);
        check("owner can delete", service.listFiles(owner).stream().noneMatch(f -> f.getId() == fileId));
        check("encrypted file removed from disk", !new File(storedPath).exists());
        check("its key is destroyed in the database", fileDAO.getEncryptionKey(fileId).isEmpty());

        // ---------- 6. Account security ----------
        System.out.println("6. Account security");
        boolean weakBlocked = false;
        try { userService.registerUser("weakuser", "abc"); }
        catch (IllegalArgumentException e) { weakBlocked = true; }
        check("weak password is rejected", weakBlocked);

        boolean badNameBlocked = false;
        try { userService.registerUser("bad name!", "goodpass123"); }
        catch (IllegalArgumentException e) { badNameBlocked = true; }
        check("invalid username is rejected", badNameBlocked);

        String name = "newuser" + System.currentTimeMillis();
        User fresh = userService.registerUser(name, "pass1234");
        check("new sign-up starts as VIEWER", fresh.getRole() == Role.VIEWER);

        boolean duplicateBlocked = false;
        try { userService.registerUser(name, "pass1234"); }
        catch (IllegalArgumentException e) { duplicateBlocked = true; }
        check("duplicate username is rejected", duplicateBlocked);

        boolean ownerGrabBlocked = false;
        try { userService.registerFirstOwner("sneaky" + System.currentTimeMillis(), "pass1234"); }
        catch (SecurityException e) { ownerGrabBlocked = true; }
        check("first-owner setup is refused once accounts exist", ownerGrabBlocked && !userService.isFirstRun());

        for (int i = 0; i < 5; i++) userService.login(name, "wrong-password-1");
        boolean lockedOut = false;
        try { userService.login(name, "pass1234"); }   // even the CORRECT password is refused now
        catch (SecurityException e) { lockedOut = true; }
        check("account locks after 5 wrong passwords", lockedOut);

        String name2 = "changepw" + System.currentTimeMillis();
        User pwUser = userService.registerUser(name2, "oldpass123");
        boolean wrongCurrent = false;
        try { userService.changePassword(pwUser, "not-my-password1", "newpass456", "newpass456"); }
        catch (SecurityException e) { wrongCurrent = true; }
        check("password change needs the current password", wrongCurrent);
        userService.changePassword(pwUser, "oldpass123", "newpass456", "newpass456");
        check("new password works, old one does not",
                userService.login(name2, "newpass456") != null && userService.login(name2, "oldpass123") == null);

        // ---------- 7. Role management ----------
        System.out.println("7. Role management");
        boolean roleChangeBlocked = false;
        try { userService.changeRole(viewer, pwUser.getId(), Role.OWNER); }
        catch (SecurityException e) { roleChangeBlocked = true; }
        check("non-owner cannot change roles", roleChangeBlocked);

        userService.changeRole(owner, pwUser.getId(), Role.EDITOR);
        User promoted = users.getUserByUsername(name2);
        check("owner can promote a user to EDITOR", promoted.getRole() == Role.EDITOR);
        int promotedUpload = service.uploadFile(promoted, original, Role.VIEWER);
        check("promoted user can now upload", promotedUpload > 0);

        userService.changeRole(owner, pwUser.getId(), Role.VIEWER);   // demote; 'promoted' still says EDITOR
        boolean staleBlocked = false;
        try { service.uploadFile(promoted, original, Role.VIEWER); }
        catch (SecurityException e) { staleBlocked = true; }
        check("a demoted user loses access at once (role read from DB)", staleBlocked);

        userService.setUserActive(owner, pwUser.getId(), false);
        boolean disabledBlocked = false;
        try { userService.login(name2, "newpass456"); }
        catch (SecurityException e) { disabledBlocked = true; }
        check("a disabled account cannot log in", disabledBlocked);
        userService.setUserActive(owner, pwUser.getId(), true);
        check("re-enabled account can log in again", userService.login(name2, "newpass456") != null);

        // ---------- 8. Audit log ----------
        System.out.println("8. Audit log");
        boolean sawDenied = false;
        for (AuditLogEntry e : audit.getAllLogs()) {
            if (e.getStatus().equals("DENIED")) sawDenied = true;
        }
        check("denied attempts are logged", sawDenied);
        check("audit chain is intact", audit.verifyIntegrity());

        int lastId = 0;
        String lastAction = null;
        try (Connection c = DatabaseManager.connect();
             PreparedStatement ps = c.prepareStatement("SELECT id, action FROM audit_logs ORDER BY id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            lastId = rs.getInt(1);
            lastAction = rs.getString(2);
        }
        boolean editBlocked = false;
        try { setAction(lastId, "HACKED"); }
        catch (java.sql.SQLException e) { editBlocked = true; }
        check("database refuses to edit an audit row (trigger)", editBlocked);

        // Simulate an attacker who removes the trigger first, then edits the row.
        runSql("DROP TRIGGER audit_no_update");
        setAction(lastId, "HACKED");
        boolean tamperDetected = !audit.verifyIntegrity();
        setAction(lastId, lastAction);   // put the original back
        DatabaseManager.initializeTables();   // re-creates the trigger
        check("editing a log entry is detected (hash chain)", tamperDetected);
        check("chain is valid again after restoring it", audit.verifyIntegrity());

        System.out.println("   Latest entries:");
        audit.getAllLogs().stream().limit(6).forEach(e -> System.out.println("   "
                + e.getTimestamp() + " | " + e.getUsername() + " | " + e.getAction()
                + " | " + e.getFilename() + " | " + e.getStatus()));

        original.delete();
        restored.delete();
        restored2.delete();
        System.out.println("\nResult: " + passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static void runSql(String sql) throws Exception {
        try (Connection c = DatabaseManager.connect(); java.sql.Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** Test helper: changes an audit row directly in the database, the way an attacker would. */
    private static void setAction(int id, String action) throws Exception {
        try (Connection c = DatabaseManager.connect();
             PreparedStatement ps = c.prepareStatement("UPDATE audit_logs SET action = ? WHERE id = ?")) {
            ps.setString(1, action);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }
}
