package com.sentinelvault.ui;

import com.sentinelvault.model.FileEntry;
import com.sentinelvault.model.Role;

import java.awt.*;
import java.io.File;

/** Upload, download and delete, shared by the Vault and Shared screens. */
final class FileActions {
    private FileActions() {}

    static void upload(Ctx ctx, Component parent, Runnable done) {
        FileDialog fd = new FileDialog((Frame) ctx.window(), "Choose a file to encrypt", FileDialog.LOAD);
        fd.setVisible(true);
        if (fd.getFile() == null) return;
        File source = new File(fd.getDirectory(), fd.getFile());

        String[] labels = {"Everyone  (Viewers, Editors, Owners)", "Editors and Owners", "Owners only"};
        Role[] roles = {Role.VIEWER, Role.EDITOR, Role.OWNER};
        Widgets.Combo<String> combo = new Widgets.Combo<>(labels);
        boolean ok = Dialogs.form(parent, "Who can open this file?",
                "“" + source.getName() + "” will be encrypted with its own key before it is stored.",
                Dialogs.labelled("Access level", combo), "Encrypt & upload", false);
        if (!ok) return;
        Role level = roles[combo.getSelectedIndex()];

        ctx.toast("Encrypting " + source.getName() + "…", false);
        Async.run(() -> ctx.api().upload(ctx.user(), source, level),
                id -> { ctx.toast("Encrypted and stored: " + source.getName(), false); done.run(); },
                t -> ctx.toast(Async.message(t), true));
    }

    static void download(Ctx ctx, FileEntry file) {
        FileDialog fd = new FileDialog((Frame) ctx.window(), "Save decrypted copy", FileDialog.SAVE);
        fd.setFile(file.getFilename());
        fd.setVisible(true);
        if (fd.getFile() == null) return;
        File target = new File(fd.getDirectory(), fd.getFile());
        Async.run(() -> { ctx.api().download(ctx.user(), file.getId(), target); return null; },
                v -> ctx.toast("Decrypted and saved: " + target.getName(), false),
                t -> ctx.toast(Async.message(t), true));
    }

    static void delete(Ctx ctx, Component parent, FileEntry file, Runnable done) {
        boolean ok = Dialogs.confirm(parent, "Delete this file permanently?",
                "“" + file.getFilename() + "” will be removed and its encryption key destroyed. "
                        + "Nobody can recover it afterwards, not even with a copy of the encrypted file.",
                "Delete forever", true);
        if (!ok) return;
        Async.run(() -> { ctx.api().delete(ctx.user(), file.getId()); return null; },
                v -> { ctx.toast("Deleted " + file.getFilename() + ". Its key has been destroyed.", false); done.run(); },
                t -> ctx.toast(Async.message(t), true));
    }
}
