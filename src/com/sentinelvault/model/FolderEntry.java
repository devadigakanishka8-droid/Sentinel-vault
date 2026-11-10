package com.sentinelvault.model;

/** A folder that groups files. */
public class FolderEntry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final int id;
    private final String name;
    private final int ownerId;
    private final String createdAt;

    public FolderEntry(int id, String name, int ownerId, String createdAt) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getOwnerId() { return ownerId; }
    public String getCreatedAt() { return createdAt; }

    @Override public String toString() { return name; }
}
