package com.sentinelvault.ui;

import com.sentinelvault.model.AuditLogEntry;
import com.sentinelvault.model.Role;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Turns raw data into friendly text. */
final class Fmt {
    private Fmt() {}

    private static final DateTimeFormatter NICE = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Audit timestamps are written in local time. */
    static String audit(String ts) {
        try { return LocalDateTime.parse(ts.replace(' ', 'T')).format(NICE); } catch (Exception e) { return ts; }
    }

    /** File dates come from SQLite in UTC, so convert them to the computer's time zone. */
    static String fileDate(String utc) {
        try {
            return LocalDateTime.parse(utc.replace(' ', 'T')).atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(ZoneId.systemDefault()).format(NICE);
        } catch (Exception e) { return utc; }
    }

    static String ago(String ts) {
        try {
            LocalDateTime t = LocalDateTime.parse(ts.replace(' ', 'T'));
            Duration d = Duration.between(t, LocalDateTime.now());
            long min = Math.max(0, d.toMinutes());
            if (min < 1) return "just now";
            if (min < 60) return min + " min ago";
            if (min < 60 * 24) return (min / 60) + " h ago";
            if (min < 60 * 48) return "yesterday";
            return t.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
        } catch (Exception e) { return ts; }
    }

    static String level(Role r) {
        switch (r) {
            case OWNER:  return "Owners only";
            case EDITOR: return "Editors +";
            default:     return "Everyone";
        }
    }

    static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "FILE";
        String e = filename.substring(dot + 1).toUpperCase(Locale.ENGLISH);
        return e.length() > 4 ? e.substring(0, 4) : e;
    }

    private static String noun(String action) {
        switch (action) {
            case "UPLOAD": return "upload";
            case "DOWNLOAD": return "download";
            case "DELETE": return "delete";
            case "LOGIN": return "sign-in";
            case "LOGIN_LOCKED": return "sign-in (account locked)";
            case "ACCOUNT_LOCKED": return "account lock";
            case "REGISTER": return "sign-up";
            case "CHANGE_PASSWORD": return "password change";
            case "CHANGE_ROLE": return "role change";
            case "ENABLE_USER": return "account enable";
            case "DISABLE_USER": return "account disable";
            case "VIEW": return "file list";
            default: return action.toLowerCase(Locale.ENGLISH).replace('_', ' ');
        }
    }

    private static String verb(String action) {
        switch (action) {
            case "UPLOAD": return "uploaded";
            case "DOWNLOAD": return "downloaded";
            case "DELETE": return "deleted";
            case "LOGIN": return "signed in";
            case "REGISTER": return "created an account";
            case "CHANGE_PASSWORD": return "changed their password";
            case "CHANGE_ROLE": return "changed a user's role";
            case "ENABLE_USER": return "enabled an account";
            case "DISABLE_USER": return "disabled an account";
            case "VIEW": return "viewed the file list";
            default: return noun(action);
        }
    }

    /** One readable sentence in HTML, e.g. "<b>kanishka</b> uploaded report.pdf". */
    static String sentence(AuditLogEntry e) {
        String who = "<b>" + esc(e.getUsername()) + "</b>";
        String file = "-".equals(e.getFilename()) ? "" : " <i>" + esc(e.getFilename()) + "</i>";
        switch (e.getStatus()) {
            case "DENIED": return who + " was denied: " + noun(e.getAction()) + file;
            case "FAILED": return who + " &mdash; " + noun(e.getAction()) + " failed" + file;
            default:       return who + " " + verb(e.getAction()) + file;
        }
    }

    static String greeting() {
        int h = LocalTime.now().getHour();
        return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
    }
}
