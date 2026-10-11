package com.sentinelvault.model;

public class AuditLogEntry {
    private final String timestamp;
    private final String username;
    private final String action;
    private final String filename;   // may be "-" when the action isn't about a file
    private final String status;     // "SUCCESS" or "DENIED"

    public AuditLogEntry(String timestamp, String username, String action,
                         String filename, String status) {
        this.timestamp = timestamp;
        this.username = username;
        this.action = action;
        this.filename = filename;
        this.status = status;
    }

    public String getTimestamp() { return timestamp; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public String getFilename() { return filename; }
    public String getStatus() { return status; }
}
