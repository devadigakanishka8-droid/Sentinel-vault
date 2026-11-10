package com.sentinelvault.model;

/** One saved version of a file. Every version has its own encryption key and SHA-256. */
public class VersionEntry implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    private final int versionNo;
    private final long sizeBytes;
    private final String sha256;
    private final String uploadedBy;
    private final String uploadedAt;
    private final String comment;
    private final boolean current;
    private final boolean available;   // false once the key was destroyed

    public VersionEntry(int versionNo, long sizeBytes, String sha256, String uploadedBy,
                        String uploadedAt, String comment, boolean current, boolean available) {
        this.versionNo = versionNo;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
        this.comment = comment;
        this.current = current;
        this.available = available;
    }

    public int getVersionNo() { return versionNo; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getUploadedBy() { return uploadedBy; }
    public String getUploadedAt() { return uploadedAt; }
    public String getComment() { return comment; }
    public boolean isCurrent() { return current; }
    public boolean isAvailable() { return available; }
}
