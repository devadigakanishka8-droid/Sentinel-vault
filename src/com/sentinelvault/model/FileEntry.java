package com.sentinelvault.model;

/** A file as shown to the user: its current version, folder and who may open it. */
public class FileEntry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String filename;
    private final int ownerId;
    private final Role accessLevel;
    private final String storedPath;   // where the encrypted copy lives on disk
    private final String uploadedAt;
    private final Integer folderId;    // null = not in a folder
    private final int version;         // number of the current version

    public FileEntry(int id, String filename, int ownerId, Role accessLevel,
                     String storedPath, String uploadedAt, Integer folderId, int version) {
        this.id = id;
        this.filename = filename;
        this.ownerId = ownerId;
        this.accessLevel = accessLevel;
        this.storedPath = storedPath;
        this.uploadedAt = uploadedAt;
        this.folderId = folderId;
        this.version = version;
    }

    public FileEntry(int id, String filename, int ownerId, Role accessLevel,
                     String storedPath, String uploadedAt) {
        this(id, filename, ownerId, accessLevel, storedPath, uploadedAt, null, 1);
    }

    public int getId() { return id; }
    public String getFilename() { return filename; }
    public int getOwnerId() { return ownerId; }
    public Role getAccessLevel() { return accessLevel; }
    public String getStoredPath() { return storedPath; }
    public String getUploadedAt() { return uploadedAt; }
    public Integer getFolderId() { return folderId; }
    public int getVersion() { return version; }
}
