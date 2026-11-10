package com.sentinelvault.net;

import com.sentinelvault.model.AuditLogEntry;
import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.FolderEntry;
import com.sentinelvault.model.VersionEntry;
import com.sentinelvault.model.Role;
import com.sentinelvault.model.User;
import com.sentinelvault.net.Wire.Request;
import com.sentinelvault.net.Wire.Response;
import com.sentinelvault.ui.Api;

import javax.net.ServerSocketFactory;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * The server side of SentinelVault. It owns the vault (database, encrypted files, master key) and
 * answers requests from clients on other computers. All permission checks still happen in the
 * services behind {@link Api}; this class only translates network requests into Api calls.
 *
 * A client proves who it is with a random session token that the server issues at login.
 * The client never sends a user object, so nobody can pretend to be another user.
 */
public class VaultServer {

    private static final long SESSION_MILLIS = 8L * 60 * 60 * 1000;   // 8 hours
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;

    private record Session(String username, long expires) {}

    private final Api api;
    private final int port;
    private final ServerSocketFactory sockets;
    private final Consumer<String> log;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "vault-client");
        t.setDaemon(true);
        return t;
    });
    private volatile ServerSocket serverSocket;

    /** @param sockets use Tls.serverFactory(...) for real use; a plain factory is only for tests */
    public VaultServer(Api api, int port, ServerSocketFactory sockets, Consumer<String> log) {
        this.api = api;
        this.port = port;
        this.sockets = sockets;
        this.log = log == null ? s -> { } : log;
    }

    public void start() throws IOException {
        serverSocket = sockets.createServerSocket(port);
        Thread accept = new Thread(() -> {
            while (!serverSocket.isClosed()) {
                try {
                    Socket s = serverSocket.accept();
                    pool.execute(() -> handle(s));
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) log.accept("Accept failed: " + e.getMessage());
                }
            }
        }, "vault-accept");
        accept.setDaemon(true);
        accept.start();
        log.accept("Server listening on port " + port);
    }

    public void stop() {
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
        pool.shutdownNow();
        sessions.clear();
    }

    // ---------- one connection = one request and one answer ----------
    private void handle(Socket socket) {
        String who = socket.getInetAddress().getHostAddress();
        try (socket) {
            socket.setSoTimeout(120_000);
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            in.setObjectInputFilter(Wire.filter());
            Object obj = in.readObject();
            Response res;
            if (obj instanceof Request req) {
                res = dispatch(req, who);
            } else {
                res = Response.error("ARGUMENT", "Bad request.");
            }
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            out.writeObject(res);
            out.flush();
        } catch (Exception e) {
            log.accept("Connection from " + who + " failed: " + e.getClass().getSimpleName());
        }
    }

    Response dispatch(Request r, String who) {
        try {
            Object[] a = r.args == null ? new Object[0] : r.args;
            switch (r.op) {
                // ----- no session needed -----
                case "needsSetup":
                    return Response.ok(api.needsSetup());
                case "register":
                    return Response.ok(clean(api.register(str(a, 0), str(a, 1))));
                case "registerFirstOwner":
                    return Response.ok(clean(api.registerFirstOwner(str(a, 0), str(a, 1))));
                case "login": {
                    User u = api.login(str(a, 0), str(a, 1));
                    if (u == null) return Response.ok(null);
                    String token = newToken(u.getUsername());
                    log.accept(who + " signed in as " + u.getUsername());
                    return Response.ok(new Object[]{token, clean(u)});
                }
                default:
                    break;
            }

            // ----- everything below needs a valid session -----
            User me = authenticate(r.token);
            if (r.op.equals("refresh")) return Response.ok(me == null ? null : clean(me));
            if (me == null) throw new SecurityException("Your session has ended. Please sign in again.");

            switch (r.op) {
                case "logout":
                    if (r.token != null) sessions.remove(r.token);
                    return Response.ok(null);
                case "files": {
                    List<FileEntry> shown = new ArrayList<>();
                    for (FileEntry f : api.files(me)) {
                        // the server's folder path is never sent to clients
                        shown.add(new FileEntry(f.getId(), f.getFilename(), f.getOwnerId(),
                                f.getAccessLevel(), "", f.getUploadedAt(), f.getFolderId(), f.getVersion()));
                    }
                    return Response.ok(new Object[]{shown, new HashMap<>(api.ownerNames())});
                }
                case "upload":
                    return Response.ok(upload(me, str(a, 0), (byte[]) a[1], (Role) a[2]));
                case "download":
                    return Response.ok(download(me, (Integer) a[0]));
                case "delete":
                    api.delete(me, (Integer) a[0]);
                    return Response.ok(null);
                case "folders":
                    return Response.ok(new ArrayList<FolderEntry>(api.folders(me)));
                case "createFolder":
                    return Response.ok(api.createFolder(me, str(a, 0)));
                case "moveFile":
                    api.moveFile(me, (Integer) a[0], (Integer) a[1]);
                    return Response.ok(null);
                case "deleteFolder":
                    api.deleteFolder(me, (Integer) a[0]);
                    return Response.ok(null);
                case "versions":
                    return Response.ok(new ArrayList<VersionEntry>(api.versions(me, (Integer) a[0])));
                case "uploadVersion":
                    return Response.ok(uploadVersion(me, (Integer) a[0], str(a, 1), (byte[]) a[2], str(a, 3)));
                case "downloadVersion":
                    return Response.ok(downloadVersion(me, (Integer) a[0], (Integer) a[1]));
                case "restoreVersion":
                    api.restoreVersion(me, (Integer) a[0], (Integer) a[1]);
                    return Response.ok(null);
                case "audit":
                    return Response.ok(new ArrayList<AuditLogEntry>(api.audit(me)));
                case "verifyAudit":
                    return Response.ok(api.verifyAudit(me));
                case "users":
                    return Response.ok(new ArrayList<>(api.users(me)));
                case "changeRole":
                    api.changeRole(me, (Integer) a[0], (Role) a[1]);
                    return Response.ok(null);
                case "setActive":
                    api.setActive(me, (Integer) a[0], (Boolean) a[1]);
                    return Response.ok(null);
                case "changePassword":
                    api.changePassword(me, str(a, 0), str(a, 1), str(a, 2));
                    return Response.ok(null);
                default:
                    return Response.error("ARGUMENT", "Unknown request.");
            }
        } catch (SecurityException e) {
            return Response.error("SECURITY", e.getMessage());
        } catch (IllegalArgumentException e) {
            return Response.error("ARGUMENT", e.getMessage());
        } catch (IllegalStateException e) {
            return Response.error("STATE", e.getMessage());
        } catch (ClassCastException | ArrayIndexOutOfBoundsException | NullPointerException e) {
            return Response.error("ARGUMENT", "Bad request.");
        } catch (Exception e) {
            log.accept("Error in " + r.op + ": " + e);
            return Response.error("ERROR", "The server could not complete the request.");
        }
    }

    // ---------- files: bytes arrive here, the normal service does the encryption ----------
    private int upload(User me, String filename, byte[] data, Role level) throws Exception {
        if (data == null || data.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File is too large (max 50 MB).");
        }
        String name = safeName(filename);
        Path dir = Files.createTempDirectory("sv-up");
        File tmp = new File(dir.toFile(), name);
        try {
            Files.write(tmp.toPath(), data);
            int id = api.upload(me, tmp, level);
            log.accept(me.getUsername() + " uploaded " + name);
            return id;
        } finally {
            tmp.delete();
            dir.toFile().delete();
        }
    }

    private int uploadVersion(User me, int fileId, String filename, byte[] data, String comment) throws Exception {
        if (data == null || data.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("File is too large (max 50 MB).");
        }
        String name = safeName(filename);
        Path dir = Files.createTempDirectory("sv-ver");
        File tmp = new File(dir.toFile(), name);
        try {
            Files.write(tmp.toPath(), data);
            int versionNo = api.uploadVersion(me, fileId, tmp, comment);
            log.accept(me.getUsername() + " uploaded version " + versionNo + " of file " + fileId);
            return versionNo;
        } finally {
            tmp.delete();
            dir.toFile().delete();
        }
    }

    private byte[] downloadVersion(User me, int fileId, int versionNo) throws Exception {
        Path dir = Files.createTempDirectory("sv-vdown");
        File tmp = new File(dir.toFile(), "out.bin");
        try {
            api.downloadVersion(me, fileId, versionNo, tmp);
            log.accept(me.getUsername() + " downloaded version " + versionNo + " of file " + fileId);
            return Files.readAllBytes(tmp.toPath());
        } finally {
            tmp.delete();
            dir.toFile().delete();
        }
    }

    private byte[] download(User me, int fileId) throws Exception {
        Path dir = Files.createTempDirectory("sv-down");
        File tmp = new File(dir.toFile(), "out.bin");
        try {
            api.download(me, fileId, tmp);
            log.accept(me.getUsername() + " downloaded file " + fileId);
            return Files.readAllBytes(tmp.toPath());
        } finally {
            tmp.delete();
            dir.toFile().delete();
        }
    }

    /** Keeps only a plain file name: no folders, no "..". */
    static String safeName(String filename) {
        String n = filename == null ? "" : filename.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) throw new IllegalArgumentException("Choose a valid file.");
        return Paths.get(n).getFileName().toString();
    }

    // ---------- sessions ----------
    private String newToken(String username) {
        long now = System.currentTimeMillis();
        sessions.values().removeIf(s -> s.expires() < now);
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        sessions.put(token, new Session(username, now + SESSION_MILLIS));
        return token;
    }

    /** The current, fresh user for this token, or null if the token is unknown, expired or the account is disabled. */
    private User authenticate(String token) throws Exception {
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null || s.expires() < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        User fresh = api.refresh(new User(0, s.username(), "", null));
        if (fresh == null) sessions.remove(token);
        return fresh;
    }

    /** Never send the password hash to a client. */
    private static User clean(User u) {
        return u == null ? null : new User(u.getId(), u.getUsername(), "", u.getRole());
    }

    private static String str(Object[] a, int i) {
        Object o = a[i];
        return o == null ? null : (String) o;
    }
}
