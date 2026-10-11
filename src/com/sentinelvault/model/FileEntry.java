package com.sentinelvault.model;

public class FileEntry {
    private final int id;
    private final String filename;
    private final int ownerId;
    private final Role accessLevel;
    private final String storedPath;   // where the encrypted copy lives on disk
    private final String uploadedAt;

    public FileEntry(int id, String filename, int ownerId, Role accessLevel,
                     String storedPath, String uploadedAt) {
        this.id = id;
        this.filename = filename;
        this.ownerId = ownerId;
        this.accessLevel = accessLevel;
        this.storedPath = storedPath;
        this.uploadedAt = uploadedAt;
    }

    public int getId() { return id; }
    public String getFilename() { return filename; }
    public int getOwnerId() { return ownerId; }
    public Role getAccessLevel() { return accessLevel; }
    public String getStoredPath() { return storedPath; }
    public String getUploadedAt() { return uploadedAt; }
}
