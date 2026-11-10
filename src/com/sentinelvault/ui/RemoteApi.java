package com.sentinelvault.ui;

import com.sentinelvault.model.AuditLogEntry;
import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.FolderEntry;
import com.sentinelvault.model.VersionEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;
import com.sentinelvault.net.Wire;

import javax.net.SocketFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The same Api as BackendApi, but every call goes over the network to a VaultServer.
 * The screens cannot tell the difference. No rules live here: the server decides everything.
 */
public class RemoteApi implements Api {

    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    private final String host;
    private final int port;
    private final SocketFactory sockets;
    private volatile String token;
    private volatile Map<Integer, String> names = new HashMap<>();

    public RemoteApi(String host, int port, SocketFactory sockets) {
        this.host = host;
        this.port = port;
        this.sockets = sockets;
    }

    // ---------- the one place that talks to the network ----------
    private Object call(String op, Object... args) throws Exception {
        Wire.Request req = new Wire.Request(op, token, args);
        try (Socket socket = sockets.createSocket()) {
            socket.connect(new InetSocketAddress(host, port), 5_000);
            socket.setSoTimeout(120_000);
            if (socket instanceof SSLSocket ssl) ssl.startHandshake();   // fails if the server is not ours
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(req);
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            in.setObjectInputFilter(Wire.filter());
            Wire.Response res = (Wire.Response) in.readObject();
            if (res.ok) return res.value;
            switch (res.errorKind == null ? "ERROR" : res.errorKind) {
                case "SECURITY": throw new SecurityException(res.errorMessage);
                case "ARGUMENT": throw new IllegalArgumentException(res.errorMessage);
                case "STATE":    throw new IllegalStateException(res.errorMessage);
                default:         throw new IllegalStateException(res.errorMessage);
            }
        } catch (SSLException e) {
            throw new IllegalStateException("The secure connection to " + host + ":" + port + " failed. "
                    + "Make sure tls/server.crt is the certificate of THIS server, or that the server is the real one.");
        } catch (IOException e) {
            throw new IllegalStateException("Cannot reach the vault server at " + host + ":" + port
                    + ". Check that the server is running and both laptops are on the same network.");
        }
    }

    /** Quick check used by the launcher before opening the sign-in screen. */
    public void ping() throws Exception { call("needsSetup"); }

    // ---------- accounts ----------
    @Override public User login(String username, String password) throws Exception {
        Object[] r = (Object[]) call("login", username, password);
        if (r == null) return null;
        token = (String) r[0];
        return (User) r[1];
    }

    @Override public User register(String username, String password) throws Exception {
        return (User) call("register", username, password);
    }

    @Override public boolean needsSetup() throws Exception {
        return (Boolean) call("needsSetup");
    }

    @Override public User registerFirstOwner(String username, String password) throws Exception {
        return (User) call("registerFirstOwner", username, password);
    }

    @Override public User refresh(User user) throws Exception {
        if (token == null) return null;
        try {
            return (User) call("refresh");
        } catch (SecurityException e) {
            return null;
        }
    }

    /** Ends the session on the server. Called when the user signs out. */
    public void logout() {
        String t = token;
        if (t == null) return;
        try { call("logout"); } catch (Exception ignored) { }
        token = null;
    }

    // ---------- files ----------
    @Override @SuppressWarnings("unchecked")
    public List<FileEntry> files(User user) throws Exception {
        Object[] r = (Object[]) call("files");
        names = (Map<Integer, String>) r[1];
        return (List<FileEntry>) r[0];
    }

    @Override public String ownerName(int userId) {
        return names.getOrDefault(userId, "user " + userId);
    }

    @Override public Map<Integer, String> ownerNames() { return names; }

    @Override public int upload(User user, File source, Role accessLevel) throws Exception {
        if (source == null || !source.isFile()) throw new IllegalArgumentException("Choose a valid file.");
        if (source.length() > MAX_FILE_BYTES) throw new IllegalArgumentException("File is too large (max 50 MB).");
        return (Integer) call("upload", source.getName(), Files.readAllBytes(source.toPath()), accessLevel);
    }

    @Override public void download(User user, int fileId, File destination) throws Exception {
        byte[] data = (byte[]) call("download", fileId);
        Files.write(destination.toPath(), data);
    }

    @Override public void delete(User user, int fileId) throws Exception {
        call("delete", fileId);
    }

    // ---------- folders ----------
    @Override @SuppressWarnings("unchecked")
    public List<FolderEntry> folders(User user) throws Exception { return (List<FolderEntry>) call("folders"); }

    @Override public int createFolder(User user, String name) throws Exception { return (Integer) call("createFolder", name); }

    @Override public void moveFile(User user, int fileId, Integer folderId) throws Exception { call("moveFile", fileId, folderId); }

    @Override public void deleteFolder(User user, int folderId) throws Exception { call("deleteFolder", folderId); }

    // ---------- versions ----------
    @Override @SuppressWarnings("unchecked")
    public List<VersionEntry> versions(User user, int fileId) throws Exception { return (List<VersionEntry>) call("versions", fileId); }

    @Override public int uploadVersion(User user, int fileId, File source, String comment) throws Exception {
        if (source == null || !source.isFile()) throw new IllegalArgumentException("Choose a valid file.");
        if (source.length() > MAX_FILE_BYTES) throw new IllegalArgumentException("File is too large (max 50 MB).");
        return (Integer) call("uploadVersion", fileId, source.getName(), Files.readAllBytes(source.toPath()), comment);
    }

    @Override public void downloadVersion(User user, int fileId, int versionNo, File destination) throws Exception {
        Files.write(destination.toPath(), (byte[]) call("downloadVersion", fileId, versionNo));
    }

    @Override public void restoreVersion(User user, int fileId, int versionNo) throws Exception { call("restoreVersion", fileId, versionNo); }

    // ---------- audit and administration ----------
    @Override @SuppressWarnings("unchecked")
    public List<AuditLogEntry> audit(User user) throws Exception {
        return (List<AuditLogEntry>) call("audit");
    }

    @Override public boolean verifyAudit(User user) throws Exception {
        return (Boolean) call("verifyAudit");
    }

    @Override @SuppressWarnings("unchecked")
    public List<UserRow> users(User user) throws Exception {
        return (List<UserRow>) call("users");
    }

    @Override public void changeRole(User actor, int targetId, Role role) throws Exception {
        call("changeRole", targetId, role);
    }

    @Override public void setActive(User actor, int targetId, boolean active) throws Exception {
        call("setActive", targetId, active);
    }

    @Override public void changePassword(User user, String current, String next, String confirm) throws Exception {
        call("changePassword", current, next, confirm);
    }
}
