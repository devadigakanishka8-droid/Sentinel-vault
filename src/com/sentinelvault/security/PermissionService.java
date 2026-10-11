package com.sentinelvault.security;

import com.sentinelvault.model.Role;

/**
 * Role-Based Access Control (RBAC): decides what each role is allowed to do.
 *
 *   OWNER  -> everything (including delete and managing roles)
 *   EDITOR -> view, download, upload, share
 *   VIEWER -> view and download only
 */
public class PermissionService {

    public static final String VIEW = "VIEW";
    public static final String DOWNLOAD = "DOWNLOAD";
    public static final String UPLOAD = "UPLOAD";
    public static final String SHARE = "SHARE";
    public static final String DELETE = "DELETE";
    public static final String MANAGE_ROLES = "MANAGE_ROLES";

    /**
     * File-level access: a file's access level is the lowest role allowed to open it.
     * (OWNER is the highest role, so a smaller ordinal means more privilege.)
     * The user who uploaded the file can always open it.
     */
    public static boolean canAccessFile(Role userRole, Role requiredLevel, boolean isUploader) {
        return isUploader || userRole.compareTo(requiredLevel) <= 0;
    }

    public static boolean canPerform(Role role, String action) {
        switch (role) {
            case OWNER:
                return true;
            case EDITOR:
                return action.equals(VIEW) || action.equals(DOWNLOAD)
                        || action.equals(UPLOAD) || action.equals(SHARE);
            case VIEWER:
                return action.equals(VIEW) || action.equals(DOWNLOAD);
            default:
                return false;
        }
    }
}
